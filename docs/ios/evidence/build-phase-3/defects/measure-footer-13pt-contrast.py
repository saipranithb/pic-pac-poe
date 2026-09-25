"""Read-only RGB/gradient proof for the retained 13-point footer diagnostic."""
import collections, hashlib, json, pathlib, struct, zlib
path=pathlib.Path(__file__).with_name('probability-footer-13pt-before.png')
b=path.read_bytes();pos=8;packed=[];srgb=False
assert hashlib.sha256(b).hexdigest()=='6722d34af58ac75074fc7fd11719d7715a5140bc71e0c4a066448946878c7d9a'
while pos<len(b):
 n=struct.unpack('>I',b[pos:pos+4])[0];kind=b[pos+4:pos+8];data=b[pos+8:pos+8+n]
 if kind==b'IHDR':w,h,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',data)
 if kind==b'IDAT':packed.append(data)
 if kind==b'sRGB':srgb=True
 pos+=n+12
assert (depth,color,compression,filtering,interlace)==(8,2,0,0,0) and srgb
raw=zlib.decompress(b''.join(packed));stride=w*3;previous=bytearray(stride);counter=collections.Counter();background_counter=collections.Counter();pos=0
x0,y0,x1,y1=108,2393,792,2441
for y in range(y1):
 filt=raw[pos]; row=bytearray(raw[pos+1:pos+1+stride]);pos+=stride+1
 for i in range(stride):
  a=row[i-3] if i>=3 else 0;b0=previous[i];c=previous[i-3] if i>=3 else 0
  if filt==1:predict=a
  elif filt==2:predict=b0
  elif filt==3:predict=(a+b0)//2
  elif filt==4:
   p=a+b0-c;pa=abs(p-a);pb=abs(p-b0);pc=abs(p-c);predict=a if pa<=pb and pa<=pc else b0 if pb<=pc else c
  else:assert filt==0;predict=0
  row[i]=(row[i]+predict)&255
 if y>=y0:
  counter.update(tuple(row[i:i+3]) for i in range(x0*3,x1*3,3))
  background_counter.update(tuple(row[i:i+3]) for i in range(804*3,876*3,3))
 previous=row
bg=(255,249,236);fg=(105,85,67)
def luminance(rgb):
 linear=[v/255/12.92 if v/255<=0.04045 else ((v/255+0.055)/1.055)**2.4 for v in rgb]
 return sum(a*b for a,b in zip(linear,[0.2126,0.7152,0.0722]))
ratio=(luminance(fg)+0.05)/(luminance(bg)+0.05)

backgrounds=list(background_counter.items())
assert all(rgb[0]>=253 and rgb[1]>=248 and rgb[2]>=235 for rgb,count in backgrounds)
ratios=[((luminance(rgb)+0.05)/(luminance(fg)+0.05),rgb,count) for rgb,count in backgrounds]
assert counter[fg]>1000 and sum(count for rgb,count in backgrounds)>3000
result={'schemaVersion':1,'image':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'dimensions':[w,h],'pngColorSpace':'sRGB chunk, 8-bit RGB','inputRectPoints':[36,797.7,227.7,15.7],'sampleRectPixels':[x0,y0,x1,y1],'foregroundInteriorRGB':fg,'foregroundInteriorPixelCount':counter[fg],'backgroundSampleRectPixels':[804,y0,876,y1],'backgroundGradientRGBandCounts':sorted(backgrounds),'backgroundPixelCount':sum(count for rgb,count in backgrounds),'minimumMeasuredContrast':min(ratios)[0],'minimumContrastBackgroundRGB':min(ratios)[1],'maximumMeasuredContrast':max(ratios)[0],'normalTextMinimum':4.5,'pass':min(ratios)[0]>=4.5,'mostFrequentColors':[{'rgb':rgb,'count':count} for rgb,count in counter.most_common(12)],'method':'Decode original PNG bytes without resizing; unfilter native RGB scanlines; count opaque foreground interiors within reported bounds and every adjacent unobscured gradient background pixel on the same scanlines. The source card gradient is vertical; this adjacent sample represents the text backdrop without antialiasing blends. WCAG contrast range over those background colors. Antialiased edge blends do not define authored text color.'}
print(json.dumps(result,indent=2))
