Urban LiDAR flight path optimisation
---
A dual parallel computing framework with a genetic algorithm for urban LiDAR flight path optimisation

This software is derived from a research on applications of high-performance computing for optimising airborne LiDAR data acquisition conducted at New York University's Center for Urban Science & Progress. An academic publication reporting details of the research and the derived software is currently in review. A video preview of the point cloud acquired by the optimal flight paths is available [here](https://youtu.be/xjaXgecgzbs).

## Compilation

See [compile.sh](compile.sh)

## Usage

### Compute fitness score

Compute fitness score of a specific flight grid

```bash
spark-submit --class vo.av.fly.evaluator.ComputeFitness --master yarn --deploy-mode client  --num-executors <executors> --executor-cores <cores> --driver-memory <driver_mem> --executor-memory <executor_mem> fo-evaluator-jar-with-dependencies.jar  -i <in_pcloud> -ls <line_spacing> -ss <sample_spacing> -alt <altitude> -agl <angular_params> -orientation <theta> -shift <x_y> -offset <offset_params> -p <partitions>
```

| Parameter | Description | Example |
|--|--|--|
| executors | num. executors | 4 |
| cores | num. cores per executor  | 4 |
| driver_mem | driver's memory  | 800m  |
| executor_memory | executor's memory | 4g |
| in_pcloud | path to input point cloud  | fly/pcloud/sp17 |
| line_spacing | flight line spacing (sl) | 80 |
| sample_spacing | waypoint spacing (sw)  | .25 |
| altitude | flight altitude | 300 |
| angular_params | angular parameters | -30 30 .03 |
| theta | flight grid orientation | 45 |
| x_y | flight grid position | 30 70 |
| offset_params | point cloud offset | -298000 -52850 0 |
| partitions | point cloud file partitions  | 4 |


### Call Evaluator

Call an evaluator and wait for connection to an optimiser at a port. All evaluators must be started before starting the optimiser.

```bash
spark-submit --class vo.av.fly.evaluator.Evaluate target/fo-evaluator-jar-with-dependencies.jar -port <port>
```

| Parameter | Description | Example |
|--|--|--|
| port | port to optimiser | 11111 |


### Call Optimiser

Call a GA optimiser and connect it to evaluators through specified ports

```bash
java -cp fo-optimiser-jar-with-dependencies.jar vo.av.fly.optimiser.Optimise -evaluator dummy -codec bitvector -population_size 64 -nr_survivors <survivors> -ls <line_spacing> -ss <sample_spacing> -alt <altitude> -agl <angular_params> -u -generations <generations> -mutation_rate <mutation_rate> -crossover_rate .8 -steady_fitness_termination <steady_term> -seed <seed> -log <log_file> -i <in_pcloud> -ports <ports>
```

| Parameter | Description | Example |
|--|--|--|
| survivors | num. survivors | 3 |
| line_spacing | flight line spacing (sl) | 80 |
| sample_spacing | waypoint spacing (sw)  | .25 |
| altitude | flight altitude | 300 |
| angular_params | angular parameters | -30 30 .03 |
| generations | max. num. generations | 30 |
| mutation_rate | mutation rate | .05 |
| crossover_rate | crossover rate | .8 |
| steady_term | num. steady generations for early termination | 3 |
| seed | seed number | 1 |
| log_file | log file | log/a.tsv |
| in_pcloud | path to input point cloud  | fly/pcloud/sp17 |
| port | ports to evaluators | 11111,11112,11113 |

### Prune facade points

```bash
java -cp target/fly-jar-with-dependencies.jar vo.av.fly.pre.PruneFacadePoints -i <in_pcloud> -o <out_pcloud> -r <radius> -tolerance <tolerance>
```

| Parameter | Description | Example |
|--|--|--|
| in_pcloud | path to input point cloud |  |
| out_pcloud | path to output point coud |  |
| r | neighbourhood radius | .5 |
| tolerance | tolerance | .25 |

### License

Copyright 2021 Anh Vu Vo
   
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.