"""Read-only pixel proof for the retained AI Lab audit diagnostic frame."""
import collections, hashlib, json, pathlib, struct, zlib
path=pathlib.Path(__file__).with_name('scroll-safe-area-before.png')
b=path.read_bytes();pos=8;packed=[];srgb=False
assert hashlib.sha256(b).hexdigest()=='4e1584a075273e756f05949d63c43ea6285cd354d7efb658c210b147058ecb21'
while pos<len(b):
 n=struct.unpack('>I',b[pos:pos+4])[0];kind=b[pos+4:pos+8];data=b[pos+8:pos+8+n]
 if kind==b'IHDR':w,h,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',data)
 if kind==b'IDAT':packed.append(data)
 if kind==b'sRGB':srgb=True
 pos+=n+12
assert (depth,color,compression,filtering,interlace)==(8,2,0,0,0) and srgb
raw=zlib.decompress(b''.join(packed));stride=w*3;previous=bytearray(stride);counter=collections.Counter();pos=0
x0,y0,x1,y1=60,353,1081,547
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
 if y>=y0:counter.update(tuple(row[i:i+3]) for i in range(x0*3,x1*3,3))
 previous=row
bg=(37,31,28);fg=(221,197,181)
def luminance(rgb):
 linear=[v/255/12.92 if v/255<=0.04045 else ((v/255+0.055)/1.055)**2.4 for v in rgb]
 return sum(a*b for a,b in zip(linear,[0.2126,0.7152,0.0722]))
ratio=(luminance(fg)+0.05)/(luminance(bg)+0.05)
result={'image':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'dimensions':[w,h],'pngColorSpace':'sRGB chunk, 8-bit RGB','inputRectPoints':[20,117.7,340.3,64.3],'sampleRectPixels':[x0,y0,x1,y1],'backgroundRGB':bg,'foregroundInteriorRGB':fg,'backgroundPixelCount':counter[bg],'foregroundInteriorPixelCount':counter[fg],'contrastRatio':ratio,'normalTextMinimum':4.5,'pass':ratio>=4.5,'mostFrequentColors':[{'rgb':rgb,'count':count} for rgb,count in counter.most_common(8)],'method':'Read actual PNG bytes without resizing; unfilter PNG scanlines; WCAG sRGB relative luminance of the opaque text-interior and background pixels. Antialiased edge blends are not treated as the authored foreground color.'}
assert counter[fg]>1000 and counter[bg]>10000
print(json.dumps(result,indent=2))
