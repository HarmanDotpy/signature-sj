package by.bsu.algorithms;

import by.bsu.model.IntIntPair;
import by.bsu.model.KMerDict;
import by.bsu.model.Sample;
import by.bsu.util.HammingDistance;
import by.bsu.util.KMerDictBuilder;
import by.bsu.util.LevenshteinDistance;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.LongSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import com.carrotsearch.hppc.cursors.LongCursor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Algorithm use Dirichlet method to filter pairs on sequences from sample/samples
 */
public class DirichletMethod {

    public static boolean DEBUG = true;

    public static Set<IntIntPair> run(Sample sample1, Sample sample2, KMerDict dict1, KMerDict dict2, int k) {
        Set<IntIntPair> result = new HashSet<>();
        int comps = 0;
        Set<IntIntPair> pairsToCompare = new HashSet<>();
        int kMerCoincidences = calculateCoincidences(dict1, dict2);
        if (kMerCoincidences < dict1.fixedkMersCount - k) {
            return result;
        }
        int oldLength = sample1.sequences.size() * sample2.sequences.size();
        sample1 = filterUnlikelySequences(k, dict1, dict2, sample1);
        sample2 = filterUnlikelySequences(k, dict2, dict1, sample2);

        //if we filter one of samples fully
        if (sample1.sequences.size() * sample2.sequences.size() == 0) {
            return result;
        }
        if (sample1.sequences.size() < sample2.sequences.size()) {
            Sample tmp = sample1;
            sample1 = sample2;
            sample2 = tmp;
        }
        if (sample1.sequences.size() * sample2.sequences.size() < oldLength) {
            dict1 = KMerDictBuilder.getDict(sample1, dict1.l);
            dict2 = KMerDictBuilder.getDict(sample2, dict2.l);
        }
        boolean sameLength = sample1.sequences.values().iterator().next().length() ==
                sample2.sequences.values().iterator().next().length();
        for (Map.Entry<Integer, String> seqEntity : sample1.sequences.entrySet()) {
            IntIntMap possibleSequences = new IntIntHashMap(dict2.sequencesNumber);
            int seq = seqEntity.getKey();
            List<IntIntPair> tuples = getSortedTuplesTwoSamples(dict1, dict2, seqEntity);
            for (int i = 0; i < tuples.size(); i++) {
                if (i <= tuples.size() - (dict1.fixedkMersCount - k)) {
                    for (IntCursor possibleSeq :
                            dict2.hashToSequencesMap
                                    .get(dict1.sequenceFixedPositionHashesList.get(seq)[tuples.get(i).l])
                            ) {
                        possibleSequences.putOrAdd(possibleSeq.value, 1, 1);
                    }
                } else {
                    IntIntMap tmp = new IntIntHashMap(possibleSequences.size());
                    for (IntIntCursor entry : possibleSequences) {
                        long hash = dict1.sequenceFixedPositionHashesList.get(seqEntity.getKey())[tuples.get(i).l];
                        boolean isInSecondDict = dict2.hashToSequencesMap.get(hash).contains(entry.key);
                        if (isInSecondDict ||
                                dict1.fixedkMersCount - k <= entry.value + tuples.size() - i) {
                            int add = isInSecondDict ? 1 : 0;
                            tmp.put(entry.key, entry.value + add);
                        }
                    }
                    possibleSequences = tmp;
                }
            }
            for (IntIntCursor s : possibleSequences) {
                if (!seqEntity.getKey().equals(s.key)
                        && s.value >= dict1.fixedkMersCount - k) {
                    pairsToCompare.add(new IntIntPair(seqEntity.getKey(), s.key));
                }
            }
        }
        LevenshteinDistance distance = new LevenshteinDistance(k);
        HammingDistance hammingDistance = new HammingDistance();
        int reduce = 0;
        for (IntIntPair pair : pairsToCompare) {
            comps++;
            if (sameLength && hammingDistance.apply(sample1.sequences.get(pair.l), sample2.sequences.get(pair.r)) <= k) {
                result.add(pair);
                reduce++;
                continue;
            }
            int d = distance.apply(sample1.sequences.get(pair.l), sample2.sequences.get(pair.r));
            if (d != -1) {
                result.add(pair);
            }
        }
        if (DEBUG) {
            System.out.println("comps = " + comps);
            System.out.println("reduce = " + reduce);
            System.out.println("length = " + result.size());
        }
        if (!result.isEmpty()) {
            System.out.printf("Found %s %s%n", sample1.name, sample2.name);
        }
        return result;
    }

    public static long run(Sample sample, KMerDict dict, int k) throws IOException {
        System.out.println("Start Dirihlet method for " + sample.name + " k=" + k + " l=" + dict.l);
        Set<Integer> processed = new HashSet<>();
        long[] iter = {0, 0};

        LevenshteinDistance distance = new LevenshteinDistance(k);
        HammingDistance hammingDistance = new HammingDistance();
        StringBuilder str = new StringBuilder();
        String outputFilename = sample.name + "-output.txt";
        Files.deleteIfExists(Paths.get(outputFilename));
        Files.createFile(Paths.get(outputFilename));
        long length = 0;
        iter[0] = 0;
        for (Map.Entry<Integer, String> seqEntity : sample.sequences.entrySet()) {
            iter[0]++;
            //write to file each 400 iterations
            if (iter[0] % 400 == 0) {
                Files.write(Paths.get(outputFilename), str.toString().getBytes(), StandardOpenOption.APPEND);
                str = new StringBuilder();
                System.out.print("\r" + iter[0]);
            }
            IntIntMap possibleSequences = new IntIntHashMap(dict.sequencesNumber);
            int seq = seqEntity.getKey();
            List<IntIntPair> sortedTuples = getSortedTuplesOneSample(dict, seqEntity);
            for (int i = 0; i < sortedTuples.size(); i++) {
                if (i <= sortedTuples.size() - (dict.fixedkMersCount - k)) {
                    fillPossiblePairs(dict, possibleSequences, seq, sortedTuples, processed, i);
                } else {
                    possibleSequences = filterPossibleSequences(dict, possibleSequences, seq, k, sortedTuples, i);
                }
            }
            for (IntIntCursor s : possibleSequences) {
                if (seq <= s.key
                        && s.value >= dict.fixedkMersCount - k) {
                    iter[1]++;
                    if (hammingDistance.apply(sample.sequences.get(seq), sample.sequences.get(s.key)) <= k) {
                        length++;
                        str.append(seq).append(" ").append(s.key).append("\n");

                    } else if (distance.apply(sample.sequences.get(seq), sample.sequences.get(s.key)) != -1) {
                        length++;
                        str.append(seq).append(" ").append(s.key).append("\n");
                    }

                }
            }
            processed.add(seq);
        }
        //write the rest of computed pairs
        Files.write(Paths.get(outputFilename), str.toString().getBytes(), StandardOpenOption.APPEND);
        System.out.println();
        if (length != 0) {
            System.out.printf("Found %s%n", sample.name);
            System.out.println("comps = " + iter[1]);
            System.out.println("length = " + length);
        }
        return length;
    }

    public static Long runParallel(Sample sample, KMerDict dict, int k) throws IOException {
        System.out.println("Start Dirihlet method parallel for " + sample.name + " k= " + k + " l= " + dict.l);
        AtomicLong comps = new AtomicLong(0);
        AtomicLong length = new AtomicLong(0);
        AtomicLong seqPassed = new AtomicLong(0);
        final StringBuffer[] str = {new StringBuffer()};
        LevenshteinDistance distance = new LevenshteinDistance(k);
        HammingDistance hammingDistance = new HammingDistance();
        //TODO add configuration and option not to write result but only return length
        String outputFilename = sample.name + "-output.txt";
        Files.deleteIfExists(Paths.get(outputFilename));
        Files.createFile(Paths.get(outputFilename));
        sample.sequences.entrySet().parallelStream().forEach(seqEntity -> {
            IntIntMap possibleSequences = new IntIntHashMap(dict.sequencesNumber);
            int seq = seqEntity.getKey();
            List<IntIntPair> sortedTuples = getSortedTuplesOneSample(dict, seqEntity);
            for (int i = 0; i < sortedTuples.size(); i++) {
                if (i <= sortedTuples.size() - (dict.fixedkMersCount - k)) {
                    fillPossiblePairs(dict, possibleSequences, seq, sortedTuples, i);
                } else {
                    possibleSequences = filterPossibleSequences(dict, possibleSequences, seq, k, sortedTuples, i);
                }
            }
            for (IntIntCursor s : possibleSequences) {
                if (seqEntity.getKey() <= s.key
                        && s.value >= dict.fixedkMersCount - k) {
                    comps.incrementAndGet();
                    if (hammingDistance.apply(sample.sequences.get(seq), sample.sequences.get(s.key)) <= k) {
                        length.incrementAndGet();
                        str[0].append(seq + " " + s.key + "\n");

                    } else if (distance.apply(sample.sequences.get(seq), sample.sequences.get(s.key)) != -1) {
                        length.incrementAndGet();
                        str[0].append(seq + " " + s.key + "\n");
                    }

                }

            }
            seqPassed.incrementAndGet();
            //write each 400 iterations
            if (seqPassed.intValue() % 400 == 0) {
                writeToFileSynchronized(str, outputFilename);
                System.out.print("\r" + seqPassed.intValue());
            }
        });
        writeToFileSynchronized(str, outputFilename);
        System.out.println();
        if (length.get() > 0) {
            System.out.printf("Found %s%n", sample.name);
            System.out.println("comps = " + comps);
            System.out.println("length = " + length);
        }
        return length.get();
    }

    private static void writeToFileSynchronized(StringBuffer[] str, String outputFilename) {
        synchronized (str[0]) {
            try {
                Files.write(Paths.get(outputFilename), str[0].toString().getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.out.println("IOException for file "+outputFilename);
                e.printStackTrace();
            }
            str[0].delete(0, str[0].length());
        }
    }

    /**
     * Fills possibleSequences with appearing sequences for given position(tuples.get(iter))
     * only for non parallel run
     */
    private static void fillPossiblePairs(KMerDict dict, IntIntMap possibleSequences, int seq, List<IntIntPair> tuples, Set<Integer> processed, int iter) {
        for (IntCursor possibleSeq :
                dict.hashToSequencesMap
                        .get(dict.sequenceFixedPositionHashesList.get(seq)[tuples.get(iter).l])
                ) {
            //avoid equal pairs, do not add already processed sequences
            if (possibleSeq.value == seq || processed.contains(possibleSeq.value)) {
                continue;
            }
            possibleSequences.putOrAdd(possibleSeq.value, 1, 1);
        }
    }

    /**
     * For parallel
     */
    private static void fillPossiblePairs(KMerDict dict, IntIntMap possibleSequences, int seq, List<IntIntPair> tuples, int iter) {
        for (IntCursor possibleSeq :
                dict.hashToSequencesMap
                        .get(dict.sequenceFixedPositionHashesList.get(seq)[tuples.get(iter).l])
                ) {
            //avoid equal pairs
            if (possibleSeq.value == seq) {
                continue;
            }
            possibleSequences.putOrAdd(possibleSeq.value, 1, 1);
        }
    }

    /**
     * Filters possibleSequences by removing all sequences that doesn't contain necessary amount of equal l-mers
     * with current sequence (seq)
     */
    private static IntIntMap filterPossibleSequences(KMerDict dict, IntIntMap possibleSequences, int seq, int k, List<IntIntPair> tuples, int iter) {
        IntIntMap tmp = new IntIntHashMap();
        for (IntIntCursor entry : possibleSequences) {
            long hash = dict.sequenceFixedPositionHashesList.get(seq)[tuples.get(iter).l];
            boolean isInDict = dict.hashToSequencesMap.get(hash).contains(entry.key);
            // put if sequence hash l-mer for current fixed position or if it already has enough equal l-mers
            if (isInDict ||
                    dict.fixedkMersCount - k <= entry.value + tuples.size() - iter) {
                int add = isInDict ? 1 : 0;
                tmp.put(entry.key, entry.value + add);
            }
        }
        return tmp;
    }

    /**
     * Returns list of sorted tuples for current sequence entity by number of sequences that contain
     * l-mers from sequence entity fixed positions
     */
    private static List<IntIntPair> getSortedTuplesOneSample(KMerDict dict, Map.Entry<Integer, String> seqEntity) {
        List<IntIntPair> result = new ArrayList<>();
        //for each fixed position
        for (int i = 0; i < dict.fixedkMersCount; i++) {
            long hash = dict.sequenceFixedPositionHashesList.get(seqEntity.getKey())[i];
            if (dict.allHashesSet.contains(hash)) {
                //add tuple -> (position, amount of sequences)
                result.add(new IntIntPair(i, dict.hashToSequencesMap.get(hash).size()));
            }
        }
        //sort by amount
        result.sort(Comparator.comparing(o -> o.r));
        return result;
    }

    /**
     * removes all sequences from given sample that don't have any related sequences in second sample based on
     * amount of presented l-mers for fixed positions
     */
    private static Sample filterUnlikelySequences(int k, KMerDict dict1, KMerDict dict2, Sample sample) {
        Sample result = new Sample();
        result.name = sample.name;
        result.sequences = new HashMap<>();
        for (Map.Entry<Integer, String> seqEntity : sample.sequences.entrySet()) {
            int absenceCount = 0;
            for (long hash : dict1.sequenceFixedPositionHashesList.get(seqEntity.getKey())) {
                if (!dict2.hashToSequencesMap.containsKey(hash)) {
                    absenceCount++;
                }
            }
            if (absenceCount <= k) {
                result.sequences.put(seqEntity.getKey(), seqEntity.getValue());
            }
        }
        return result;
    }

    /**
     * see getSortedTuplesOneSample
     */
    private static List<IntIntPair> getSortedTuplesTwoSamples(KMerDict dict1, KMerDict dict2, Map.Entry<Integer, String> seqEntity) {
        List<IntIntPair> result = new ArrayList<>();
        for (int i = 0; i < dict1.fixedkMersCount; i++) {
            long hash = dict1.sequenceFixedPositionHashesList.get(seqEntity.getKey())[i];
            if (dict2.allHashesSet.contains(hash)) {
                result.add(new IntIntPair(i, dict2.hashToSequencesMap.get(hash).size()));
            }
        }
        result.sort(Comparator.comparing(o -> o.r));
        return result;
    }

    /**
     * Calculates how many l-mers from fixed positions from first dictionary are in second dictionary
     */
    private static int calculateCoincidences(KMerDict dict1, KMerDict dict2) {
        int kMerCoincidences = 0;
        for (LongSet positionHashes : dict1.wholeSampleFixedPositionHashesList) {
            for (LongCursor hash : positionHashes) {
                if (dict2.allHashesSet.contains(hash.value)) {
                    kMerCoincidences++;
                    break;
                }
            }
        }
        return kMerCoincidences;
    }
}
