package by.bsu.start;

import by.bsu.algorithms.BruteForce;
import by.bsu.algorithms.DirichletMethod;
import by.bsu.algorithms.PointsMethod;
import by.bsu.algorithms.TreeMethod;
import by.bsu.model.*;
import by.bsu.model.IntIntPair;
import by.bsu.util.*;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.runner.RunnerException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static java.util.stream.Collectors.toMap;


public class Start {
    private static Path folder = Paths.get("cleaned_independent_264");
    private static List<Sample> allFiles = new ArrayList<>();

    private static void loadAllFiles(Path folder) {
        if (allFiles.isEmpty()) {
            for (File file : folder.toFile().listFiles()) {
                try {
                    allFiles.add(new Sample(file.getName(), FasReader.readList(file.toPath())));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, RunnerException, InterruptedException, ExecutionException {
        System.out.println("Waiting for start");
        int c = System.in.read();
        if (c == 113){
            System.exit(1);
        }
        Map<String, String> settings = new HashMap<>();
        String key = null;
        for (String arg : args){
            if (arg.startsWith("-") && arg.length() > 1){
                key = arg;
            } else {
                if (key == null){
                    wrongArgumentList(arg);
                }
                settings.put(key, arg);
                key = null;
            }
        }
        int k = Integer.parseInt(settings.getOrDefault("-k", "10"));
        int l = Integer.parseInt(settings.getOrDefault("-l", "11"));
        folder = Paths.get(settings.getOrDefault("-dir", "cleaned_independent_264"));
        if (settings.get("-m") != null){
            switch (settings.get("-m")){
                case "bigData":
                    testBigDataSet(k, l);
                    break;
                case "largeRelated":
                    testLargeRelatedSamples(folder);
                    break;
                case "dirichletTest":
                    testDitichletAlgorithm(folder, k, l);
                    break;
                default:
                    wrongArgumentList(settings.get("-m"));
            }
        } else {
            testBigDataSet(k, l);
        }
    }

    private static void wrongArgumentList(String arg) {
        System.out.println("Error! Wrong argument value: "+arg+" . No argument name in for of -key");
        System.out.println("How to set arguments:");
        System.out.println("-k 10 -- threshold for sequence similarity(10 is default value)");
        System.out.println("-l 11 -- l-mer length for Dirichlet method(11 is default value)");
        System.out.println("-dir /usr/name/tmp/ -- folder with input. (cleaned_independent_264 is default value)");
        System.out.println("-m bigData -- run one of predefined methods. Methods are: bigData, largeRelated, dirichletTest. bigData is default");
        System.exit(1);
    }

    private static void testLargeRelatedSamples(Path folder) {
        System.out.println("Start testLargeRelatedSamples");
        loadAllFiles(folder);
        Sample sample = allFiles.get(165);
        Sample s1 = new Sample();
        Sample s2 = new Sample();
        s1.name = "left";
        s2.name = "right";
        s1.sequences = new HashMap<>();
        s2.sequences = new HashMap<>();
        int i =0;
        int half = sample.sequences.size()/2;
        for (Map.Entry<Integer, String> entry : sample.sequences.entrySet()){
            if (i < half){
                s1.sequences.put(entry.getKey(), entry.getValue());
            }else {
                s2.sequences.put(entry.getKey(), entry.getValue());
            }
            i++;
        }
        KMerDict k1 = KMerDictBuilder.getDict(s1, 11);
        KMerDict k2 = KMerDictBuilder.getDict(s2, 11);
        long start = System.currentTimeMillis();
        System.out.println(s1.sequences.size()*s2.sequences.size());
        DirichletMethod.run(s1, s2, k1 , k2, 3);
        System.out.println("DirichletMethod time:");
        System.out.println(System.currentTimeMillis()-start);
        start = System.currentTimeMillis();
        PointsMethod.run(s1,s2, PointsBuilder.buildPoints(s1), PointsBuilder.buildPoints(s2), 3);
        System.out.println("PointsMethod time:");
        System.out.println(System.currentTimeMillis()-start);
        start = System.currentTimeMillis();
        BruteForce.run(s1,s2, 3);
        System.out.println("BruteForce time:");
        System.out.println(System.currentTimeMillis()-start);
    }


    private static void testDitichletAlgorithm(Path folder, int k, int l) throws IOException, InterruptedException, ExecutionException {
        System.out.println("Start testDitichletAlgorithm with k="+k+" l="+l);
        long start = System.currentTimeMillis();
        loadAllFiles(folder);
        KMerDict[] kdicts = new KMerDict[allFiles.size()];
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> kmerTaskList = new ArrayList<>();
        for (int j = 0; j < allFiles.size(); j++) {
            int finalJ = j;
            kmerTaskList.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    k1[index] = KMerDictBuilder.getDict(s1, l);
                    return null;
                }

                private Sample s1;
                private KMerDict[] k1;
                private int index;

                {
                    s1 = allFiles.get(finalJ);
                    k1 = kdicts;
                    index = finalJ;
                }
            });
        }
        List<Future<Void>> future =  executor.invokeAll(kmerTaskList);
        for (Future<Void> f : future){
           f.get();
        }
        System.out.println("Time to build dictionaties = " + (System.currentTimeMillis() - start));
        executor.shutdown();
        executor = Executors.newFixedThreadPool(threads);
        List<Callable<Set<IntIntPair>>> taskList = new ArrayList<>();
        for (int j = 0; j < allFiles.size(); j++) {
            for (int fIndex = j + 1; fIndex < allFiles.size(); fIndex++) {
                taskList.add(new CallDir(allFiles.get(j), allFiles.get(fIndex), kdicts[j], kdicts[fIndex], k));
            }
        }
        List<Future<Set<IntIntPair>>> futures = executor.invokeAll(taskList);

        for (Future<Set<IntIntPair>> fut : futures) {
            fut.get();
        }
        executor.shutdown();
        System.out.println("testDitichletAlgorithm has ended with time = " + (System.currentTimeMillis() - start));
        System.out.println();
    }

    private static void testBigDataSet(int k, int l) throws IOException, ExecutionException, InterruptedException {

        Sample query = new Sample("query_close", FasReader.readList(Paths.get("test_data/query1/close.fas")));
        //query = new Sample("query_close", FasReader.readList(Paths.get("some.fas")));
//        runBruteWithTime(2, query);
//        runDirWithTime(k, l, query);
//        runTreeWithTime(2, query);

        SequencesTreeBuilder.build(query);
        //runPointsWithTime(k, query);

        System.out.println();
        query = new Sample("query_medium", FasReader.readList(Paths.get("test_data/query2/medium.fas")));
//        runBruteWithTime(k, query);
//        runDirWithTime(k, l, query);
//        SequencesTreeBuilder.build(query);
        //runPointsWithTime(k, query);

        System.out.println();
        query = new Sample("query_far", FasReader.readList(Paths.get("test_data/query3/far.fas")));
//        runBruteWithTime(k, query);
//        runDirWithTime(k, l, query);
//        runTreeWithTime(k, query);
//        SequencesTreeBuilder.build(query);
        //runPointsWithTime(k, query);

        System.out.println();
        query = new Sample("db1", FasReader.readList(Paths.get("test_data/db1/1000.fas")));
        //SequencesTreeBuilder.build(query);
        //runDirWithTime(k, l, query);
        //runTreeWithTime(k, query);

        System.out.println();
        query = new Sample("db2", FasReader.readList(Paths.get("test_data/db2/2000.fas")));
        //SequencesTreeBuilder.build(query);
        runDirWithTime(k, l, query);
        //runTreeWithTime(k, query);

        System.out.println();
        query = new Sample("db3", FasReader.readList(Paths.get("test_data/db3/4000.fas")));
        //SequencesTreeBuilder.build(query);
        runDirWithTime(k, l, query);
        //runTreeWithTime(k, query);


        System.out.println();
        query = new Sample("db4", FasReader.readList(Paths.get("test_data/db4/8000.fas")));
        //SequencesTreeBuilder.build(query);
        runDirWithTime(k, l, query);
        //runTreeWithTime(k, query);

        System.out.println();
        query = new Sample("db5", FasReader.readList(Paths.get("test_data/db5/16000.fas")));
        //SequencesTreeBuilder.build(query);
        runDirWithTime(k, l, query);
        //runTreeWithTime(k, query);

        System.out.println();
        query = new Sample("db6", FasReader.readList(Paths.get("test_data/db6/32000.fas")));
        //SequencesTreeBuilder.build(query);
        runDirWithTime(k, l, query);

        System.out.println();
        Map<Integer, String > seq = FasReader.readList(Paths.get("test_data/db7/32000 (1).fas"));
        Map<Integer, String > tmp = FasReader.readList(Paths.get("test_data/db7/32000 (2).fas"));
        int[] size = new int[1];
        size[0] = seq.size();
        tmp = tmp.entrySet().stream().collect(toMap(e -> e.getKey() + size[0], Map.Entry::getValue));
        seq.putAll(tmp);
        query = new Sample("db7", seq);
        //SequencesTreeBuilder.build(query);
        //runDirWithTime(k, l, query);

        System.out.println();
        // TODO create method for such samples
        seq = FasReader.readList(Paths.get("test_data/db8/32000.fas"));
        tmp = FasReader.readList(Paths.get("test_data/db8/32000 (2).fas"));
        size[0] = seq.size();
        tmp = tmp.entrySet().stream().collect(toMap(e -> e.getKey() + size[0], Map.Entry::getValue));
        seq.putAll(tmp);
        tmp = FasReader.readList(Paths.get("test_data/db8/32000 (3).fas"));
        size[0] = seq.size();
        tmp = tmp.entrySet().stream().collect(toMap(e -> e.getKey() + size[0], Map.Entry::getValue));
        seq.putAll(tmp);
        tmp = FasReader.readList(Paths.get("test_data/db8/32000 (4).fas"));
        size[0] = seq.size();
        tmp = tmp.entrySet().stream().collect(toMap(e -> e.getKey() + size[0], Map.Entry::getValue));
        seq.putAll(tmp);
        query = new Sample("db8", seq);
        long start = System.currentTimeMillis();
        //SequencesTreeBuilder.build(query);
        System.out.println("BUILD "+(System.currentTimeMillis() - start));
        //runDirWithTime(k, l, query);
    }

    private static void runDirWithTime(int k, int l, Sample query) throws ExecutionException, InterruptedException, IOException {
        long start;
        start = System.currentTimeMillis();
        KMerDict k1 = KMerDictBuilder.getDict(query, l);
        DirichletMethod.runParallel(query, k1 ,k);
        System.out.println("Diri "+(System.currentTimeMillis()-start));
        //System.out.println(DirichletMethod.max);
    }

    private static void runPointsWithTime(int k, Sample query) {
        long start;
        start = System.currentTimeMillis();
        PointsMethod.run(query, PointsBuilder.buildPoints(query), k);
        System.out.println("Points "+(System.currentTimeMillis()-start));
    }

    private static void runBruteWithTime(int k, Sample query) {
        long start = System.currentTimeMillis();
        Set<IntIntPair> r = BruteForce.run(query, k);
        System.out.println("Brute "+(System.currentTimeMillis()-start));
    }

    private static void runTreeWithTime(int k, Sample query) {
        long start = System.currentTimeMillis();
        TreeMethod.runV2(query, SequencesTreeBuilder.build(query), k);
        System.out.println("Tree "+(System.currentTimeMillis()-start));
    }
}
