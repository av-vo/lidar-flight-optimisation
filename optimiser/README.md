# Fly

LiDAR flight path optimisation

## Compile

Fat jar 
```bash
mvn clean package 
``` 

## Run

* Prune facade points 

```bash
java -cp target/fly-jar-with-dependencies.jar vo.av.fly.pre.PruneFacadePoints -i /home/vvo/scratch/flight-optimisation/data/pcloud/sp14.txt -o /home/vvo/scratch/flight-optimisation/data/pcloud/sp14-out.txt -r .5 -tolerance .25
```

```bash
java -cp target/fly-jar-with-dependencies.jar vo.av.fly.sandbox.Log4jTest 
```


Dummy Evolve
```bash
java -cp target/fo-optimiser-jar-with-dependencies.jar  vo.av.fly.optimiser.Optimise -evaluator dummy -codec bitvector -population_size 64 -nr_survivors 3 -ls 80 -ss 1 -alt 300 -agl "-30" 30 .05 -u -generations 50 -mutation_rate .05 -crossover_rate .8 -steady_fitness_termination 3 -seed 1 -log logs/a.tsv
```

Actual optimiser
```bash
java -cp target/fo-optimiser-jar-with-dependencies.jar vo.av.fly.optimiser.Optimise -evaluator remote -codec bitvector -population_size 64 -nr_survivors 3 -ls 80 -ss 1 -alt 300 -agl "-30" 30 .05 -u -generations 50 -mutation_rate .05 -crossover_rate .8 -steady_fitness_termination 3 -seed 1 -log logs/b.tsv -i fly/test/test.txt -port 44444
```