args = new String[]{
                "-port", "00000",
                "-i", "nothing",
                "-ls", "100",
                "-ss", "1",
                "-alt", "300",
                "-agl", "-30", "30", ".05",
                "-u",
                "-population_size", "64",
                "-generation_limit", "50",
                //"-nr_survivors", "3",
                "-mutation_rate", ".05",
                "-crossover_rate", ".8",
                "-steady_fitness_termination", "50",
                "-evaluator", "dummy",
                "-codec", "bitvector",
                "-log", "/tmp/evolve/evolve2.log",
                "-timeout", "1800000",
                "-seed", "1", //"-custom_bitvector_alterer"
        };