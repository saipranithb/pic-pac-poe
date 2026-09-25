# RL policy v1 reproducibility report

Rules identifier: `picpac-5x5-draw-before-place-v1`

Trainer: tabular negamax Q-learning

Environment seed: `7331`

Episodes: `250000`

Discount: `1.0`

Exploration: finite epsilon-greedy schedule from 1.0 to a 0.02 floor

Artifact: `app/src/main/res/raw/picpac_rl_policy_v1.bin`

Artifact bytes: `891749`
SHA-256: `9B05CC725AB8B52CECB940B6C823CB66E843ACF462511C87D2AB3E1C834152B1`

Training output:

```text
learned states: 21,207
terminal wins: 202,634
terminal draws: 47,366
mean absolute TD error: 0.2121550392
```

Evaluation used a deterministic shuffle of the complete 21,314-state decision set and selected the first 1,000 states. Each chosen action was compared with exact Expectiminimax action values:

```text
optimal-action agreement: 92.3%
mean regret: 0.0292119048
```

These results describe this artifact and sample only. They do not claim convergence or global optimality. Regenerate with:

```powershell
.\gradlew.bat :game-tools:run --args="train-rl 250000 app/src/main/res/raw/picpac_rl_policy_v1.bin 7331"
```

When invoked through Gradle, relative paths are resolved from the `game-tools` project directory; use an absolute output path when replacing the Android resource from a script or CI job.
