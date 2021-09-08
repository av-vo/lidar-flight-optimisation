
Evaluate one single flight grid
```bash
time spark-submit --class vo.av.fly.evaluator.ComputeFitness target/fo-evaluator-jar-with-dependencies.jar -i fly/test/test.txt -ls 80 -ss 1 -alt 300 -agl "-30" 30 .05 -orientation 45 -shift -1 -1
```

-> ComputeFitness2 is more optimal as it use aggregateByKey instread of groupByKey

```bash
/usr/bin/time -o time.log -a spark-submit --class vo.av.fly.evaluator.ComputeFitness2 target/fo-evaluator-jar-with-dependencies.jar -i fly/test/test.txt -p 32 -ls 80 -ss 1 -alt 300 -agl "-30" 30 .05 -orientation 45 -shift -0 -0
```

Call the evaluator
```bash
time spark-submit --class vo.av.fly.evaluator.Evaluate target/fo-evaluator-jar-with-dependencies.jar -port 44444
```

```bash
java -cp target/fo-optimiser-jar-with-dependencies.jar vo.av.fly.optimiser.Optimise -evaluator remote -codec bitvector -population_size 64 -nr_survivors 3 -ls 80 -ss 1 -alt 300 -agl "-30" 30 .05 -u -generations 50 -mutation_rate .05 -crossover_rate .8 -steady_fitness_termination 3 -seed 1 -log logs/b.tsv -i fly/test/test.txt -port 44444
```