package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.GaussianNumericAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NumericAttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class CustomEFDTNode {
    protected final int gracePeriod;
    protected final SplitCriterion splitCriterion;
    protected final Double confidence;
    protected final boolean useAdaptiveConfidence;
    protected final Double adaptiveConfidence;
    protected final String leafPrediction;
    protected final Integer minSamplesReevaluate;
    protected Integer depth;
    protected final Integer maxDepth;
    protected final Double tau;
    protected final Double tauReevaluate;
    protected final Double relMinDeltaG;
    protected final boolean binaryOnly;
    protected List<Integer> usedNominalAttributes;
    protected HashMap<Integer, Double> infogainSum = new HashMap<>();
    private InstanceConditionalTest splitTest;
    protected int numSplitAttempts = 0;
    protected final NominalAttributeClassObserver nominalObserverBlueprint;
    protected final GaussianNumericAttributeClassObserver numericObserverBlueprint = new GaussianNumericAttributeClassObserver();

    protected DoubleVector classDistributionAtTimeOfCreation;
    protected DoubleVector observedClassDistribution;
    protected AutoExpandVector<AttributeClassObserver> attributeObservers = new AutoExpandVector<>();
    protected Double seenWeight = 0.0;
    protected int nodeTime = 0;
    protected Successors successors;
    protected Attribute splitAttribute;
    protected int splitAttributeIndex;
    protected final boolean noPrePrune;
    protected int blockedAttributeIndex;
    private int deltaCount = 0;

    public CustomEFDTNode(SplitCriterion splitCriterion,
                          int gracePeriod,
                          Double confidence,
                          Double adaptiveConfidence,
                          boolean useAdaptiveConfidence,
                          String leafPrediction,
                          Integer minSamplesReevaluate,
                          Integer depth,
                          Integer maxDepth,
                          Double tau,
                          Double tauReevaluate,
                          Double relMinDeltaG,
                          boolean binaryOnly,
                          boolean noPrePrune,
                          NominalAttributeClassObserver nominalObserverBlueprint,
                          DoubleVector observedClassDistribution,
                          List<Integer> usedNominalAttributes,
                          int blockedAttributeIndex) {
        this.gracePeriod = gracePeriod;
        this.splitCriterion = splitCriterion;
        this.confidence = confidence;
        this.adaptiveConfidence = adaptiveConfidence;
        this.useAdaptiveConfidence = useAdaptiveConfidence;
        this.leafPrediction = leafPrediction;
        this.minSamplesReevaluate = minSamplesReevaluate;
        this.depth = depth;
        this.maxDepth = maxDepth;
        this.tau = tau;
        this.tauReevaluate = tauReevaluate;
        this.relMinDeltaG = relMinDeltaG;
        this.binaryOnly = binaryOnly;
        this.noPrePrune = noPrePrune;
        this.usedNominalAttributes = usedNominalAttributes != null ? usedNominalAttributes : new LinkedList<>();
        this.nominalObserverBlueprint = nominalObserverBlueprint;
        this.observedClassDistribution = observedClassDistribution != null ? observedClassDistribution : new DoubleVector();
        this.infogainSum.put(-1, 0.0); // Initialize for null split
        classDistributionAtTimeOfCreation = new DoubleVector(this.observedClassDistribution);
        this.blockedAttributeIndex = blockedAttributeIndex;
    }

    protected double computeHoeffdingBound() {
        double range = splitCriterion.getRangeOfMerit(observedClassDistribution.getArrayCopy());
        double n = seenWeight;
        return Math.sqrt(((range * range) * Math.log(1.0 / currentConfidence()))
                / (2.0 * n));
    }

    /**
     * Gets the index of the attribute in the instance,
     * given the index of the attribute in the learner.
     *
     * @param index the index of the attribute in the learner
     * @param inst the instance
     * @return the index in the instance
     */
    protected static int modelAttIndexToInstanceAttIndex(int index,
                                                         Instance inst) {
        return inst.classIndex() > index ? index : index + 1;
    }

    public double[] getObservedClassDistribution() {
        return observedClassDistribution.getArrayCopy();
    }

    public Successors getSuccessors() {
        return successors;
    }

    public Attribute getSplitAttribute() {
        return splitAttribute;
    }

    public Integer getSplitAttributeIndex() {
        return splitAttributeIndex;
    }

    public InstanceConditionalTest getSplitTest() {
        return splitTest;
    }

    public boolean setSplitTest(InstanceConditionalTest newTest) {
        if (newTest == null) {
            splitTest = null;
            return true;
        }
        if (successors != null) {
            if (successors.isNominal() && successors.isBinary()) {
                if (!(newTest instanceof NominalAttributeBinaryTest))
                    return false;
            }
            if (successors.isNominal() && !successors.isBinary()) {
                if (!(newTest instanceof NominalAttributeMultiwayTest)) {
                    return false;
                }
            }
            if (!successors.isNominal()) {
                if (!(newTest instanceof NumericAttributeBinaryTest) || !successors.isBinary())
                    return false;
            }
        }
        splitTest = newTest;
        return true;
    }

    public Integer getDepth() {
        return depth;
    }

    public List<Integer> getUsedNominalAttributes() {
        return usedNominalAttributes;
    }

    Double currentConfidence() {
        if (!useAdaptiveConfidence)
            return confidence;
        double d =  adaptiveConfidence * Math.exp(-numSplitAttempts);
        deltaCount ++;
        return d;
    }

    public AttributeSplitSuggestion[] getBestSplitSuggestions(SplitCriterion criterion) {
        List<AttributeSplitSuggestion> bestSuggestions = new LinkedList<>();
        double[] preSplitDist = observedClassDistribution.getArrayCopy();
        if (!noPrePrune) {
            // add null split as an option
            bestSuggestions.add(new AttributeSplitSuggestion(null,
                    new double[0][], criterion.getMeritOfSplit(
                    preSplitDist, new double[][]{preSplitDist})));
        }
        for (int i = 0; i < attributeObservers.size(); i++) {
            AttributeClassObserver obs = attributeObservers.get(i);
            if (obs != null) {
                AttributeSplitSuggestion bestSuggestion = obs.getBestEvaluatedSplitSuggestion(
                        criterion, preSplitDist, i, binaryOnly
                );
                if (bestSuggestion != null) {
                    bestSuggestions.add(bestSuggestion);
                }
            }
        }
        return bestSuggestions.toArray(new AttributeSplitSuggestion[bestSuggestions.size()]);
    }

    public void learnInstance(Instance instance, int totalNumInstances) {
        seenWeight += instance.weight();
        nodeTime++;
        updateStatistics(instance);
        updateObservers(instance);

        if (isLeaf() && nodeTime % gracePeriod == 0) {
            attemptInitialSplit(instance);
        }
        if (!isLeaf() && nodeTime % minSamplesReevaluate == 0) {
            reevaluateSplit(instance);
        }
        if (!isLeaf()) { //Do NOT! put this in the upper (!isleaf()) block. This is not the same since we might kill the subtree during reevaluation!
            propagateToSuccessors(instance, totalNumInstances);
        }
    }

    public double[] predict(Instance instance) {
        if (!isLeaf()) {
            CustomEFDTNode successor = getSuccessor(instance);
            if (successor == null)
                return getClassVotes();
            return successor.predict(instance);
        }
        return getClassVotes();
    }

    private CustomEFDTNode getSuccessor(Instance instance) {
        if (isLeaf())
            return null;
        Double attVal = instance.value(splitAttribute);
        return successors.getSuccessorNode(attVal);
    }

    private void attemptInitialSplit(Instance instance) {
        if (depth >= maxDepth) {
            return;
        }
        if (isPure())
            return;

        numSplitAttempts++;

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSuggestions);
        updateInfogainSum(bestSuggestions);
        AttributeSplitSuggestion xBest = bestSuggestions[bestSuggestions.length - 1];
        xBest = replaceBestSuggestionIfAttributeIsBlocked(xBest, bestSuggestions, blockedAttributeIndex);

        if (!shouldSplitLeaf(bestSuggestions, currentConfidence(), observedClassDistribution))
            return;
        if (xBest.splitTest == null) {
            // preprune - null wins
            System.out.println("preprune - null wins");
            killSubtree();
            resetSplitAttribute();
            return;
        }
        Attribute newSplitAttribute = instance.attribute(xBest.splitTest.getAttsTestDependsOn()[0]);
        makeSplit(newSplitAttribute, xBest);
        classDistributionAtTimeOfCreation = new DoubleVector(observedClassDistribution.getArrayCopy());
    }

    protected void reevaluateSplit(Instance instance) {
        numSplitAttempts++;

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSuggestions);
        if (bestSuggestions.length == 0)
            return;

        // get best split suggestions
        AttributeSplitSuggestion[] bestSplitSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSplitSuggestions);
        AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];

        double bestSuggestionAverageMerit = bestSuggestion.splitTest == null ? 0.0 : bestSuggestion.merit;
        double currentAverageMerit = getCurrentSuggestionAverageMerit(bestSuggestions);
        double deltaG = bestSuggestionAverageMerit - currentAverageMerit;
        double eps = computeHoeffdingBound();

        if (deltaG > eps || (eps < tauReevaluate && deltaG > tauReevaluate * relMinDeltaG)) {
//            System.err.println(nodeTime);

            if (bestSuggestion.splitTest == null) {
                System.out.println("preprune - null wins");
                killSubtree();
                resetSplitAttribute();
            } else {
                boolean doResplit = true;
                if (
                        getSplitTest() instanceof NumericAttributeBinaryTest
                                && getSplitTest().getAttsTestDependsOn()[0] == bestSuggestion.splitTest.getAttsTestDependsOn()[0]
                ) {
                    Set<SuccessorIdentifier> keys = successors.getKeyset();
                    for (SuccessorIdentifier key: keys) {
                        if (key.isLower()) {
                            if (argmax(bestSuggestion.resultingClassDistributions[0]) == argmax(successors.getSuccessorNode(key).observedClassDistribution.getArrayRef())) {
                                doResplit = false;
                                break;
                            }
                        }
                        else {
                            if (argmax(bestSuggestion.resultingClassDistributions[1]) == argmax(successors.getSuccessorNode(key).observedClassDistribution.getArrayRef())) {
                                doResplit = false;
                                break;
                            }
                        }
                    }
                }
                if (!doResplit) {
                    NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) bestSuggestion.splitTest;
                    successors.adjustThreshold(test.getSplitValue());
                    setSplitTest(bestSuggestion.splitTest);
                    nodeTime = 0;
                    seenWeight = 0.0;
                }
                else {
                    int instanceIndex = modelAttIndexToInstanceAttIndex(bestSuggestion.splitTest.getAttsTestDependsOn()[0], instance);
                    Attribute newSplitAttribute = instance.attribute(instanceIndex);
                    makeSplit(newSplitAttribute, bestSuggestion);
                    nodeTime = 0;
                    seenWeight = 0.0;
                }
            }
        }
    }

    protected boolean initializeSuccessors(AttributeSplitSuggestion xBest, Attribute splitAttribute) {

        boolean isNominal = splitAttribute.isNominal();
        boolean isBinary = !(xBest.splitTest instanceof NominalAttributeMultiwayTest);
        Double splitValue = null;
        if (isNominal && isBinary)
            splitValue = ((NominalAttributeBinaryTest) xBest.splitTest).getValue();
        else if (!isNominal)
            splitValue = ((NumericAttributeBinaryTest) xBest.splitTest).getSplitValue();

        Integer splitAttributeIndex = xBest.splitTest.getAttsTestDependsOn()[0];
        if (splitAttribute.isNominal()) {
            if (!isBinary) {
                for (int i = 0; i < xBest.numSplits(); i++) {
                    double[] stats = xBest.resultingClassDistributionFromSplit(i);
                    if (stats.length == 0)
                        continue;
                    CustomEFDTNode s = newNode(
                            depth + 1,
                            new DoubleVector(stats),
                            getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                    boolean success = successors.addSuccessorNominalMultiway((double) i, s);
                    if (!success) {
                        successors = null;
                        return false;
                    }
                }
                return !isLeaf();
            } else {
                double[] stats1 = xBest.resultingClassDistributionFromSplit(0);
                double[] stats2 = xBest.resultingClassDistributionFromSplit(1);
                CustomEFDTNode s1 = newNode(depth + 1, new DoubleVector(stats1), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                CustomEFDTNode s2 = newNode(depth + 1, new DoubleVector(stats2), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                boolean success = successors.addSuccessorNominalBinary(splitValue, s1);
                success = success && successors.addDefaultSuccessorNominalBinary(s2);
                if (!success) {
                    successors = null;
                    return false;
                }
                return !isLeaf();
            }
        } else {
            boolean success = successors.addSuccessorNumeric(
                    splitValue,
                    newNode(depth + 1, new DoubleVector(xBest.resultingClassDistributionFromSplit(0)), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex)),
                    true
            );
            success = success && successors.addSuccessorNumeric(
                    splitValue,
                    newNode(depth + 1, new DoubleVector(xBest.resultingClassDistributionFromSplit(1)), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex)),
                    false
            );
            if (!success) {
                successors = null;
                return false;
            }
            return !isLeaf();
        }
    }

    protected void setSplitAttribute(AttributeSplitSuggestion xBest, Attribute splitAttribute) {
        int newSplitAttributeIndex = xBest.splitTest.getAttsTestDependsOn()[0];
        this.splitAttribute = splitAttribute;
        splitAttributeIndex = newSplitAttributeIndex;
        setSplitTest(xBest.splitTest);
    }

    protected void resetSplitAttribute() {
        splitAttribute = null;
        splitAttributeIndex = -1;
        splitTest = null;
    }

    protected void killSubtree() {
        successors = null;
    }

    private double[] getClassVotes() {
        return observedClassDistribution.getArrayCopy();
    }

    private void updateStatistics(Instance instance) {
        observedClassDistribution.addToValue((int) instance.classValue(), instance.weight());
    }

    private void propagateToSuccessors(Instance instance, int totalNumInstances) {
        Double attValue = instance.value(splitAttribute);
        CustomEFDTNode successor = successors.getSuccessorNode(attValue);
        if (successor == null)
            successor = addSuccessor(instance);
        if (successor != null)
            successor.learnInstance(instance, totalNumInstances);
    }

    protected CustomEFDTNode addSuccessor(Instance instance) {
        List<Integer> usedNomAttributes = new ArrayList<>(usedNominalAttributes); //deep copy
        CustomEFDTNode successor = newNode(depth + 1, null, usedNomAttributes);
        double value = instance.value(splitAttribute);
        if (splitAttribute.isNominal()) {
            if (!successors.isBinary()) {
                boolean success = successors.addSuccessorNominalMultiway(value, successor);
                return success ? successor : null;
            } else {
                boolean success = successors.addSuccessorNominalBinary(value, successor);
                if (!success) // this is the case if the split is binary nominal but the "left" successor exists.
                    success = successors.addDefaultSuccessorNominalBinary(successor);
                return success ? successor : null;
            }
        } else {
            if (successors.lowerIsMissing()) {
                boolean success = successors.addSuccessorNumeric(value, successor, true);
                return success ? successor : null;
            } else if (successors.upperIsMissing()) {
                boolean success = successors.addSuccessorNumeric(value, successor, false);
                return success ? successor : null;
            }
        }
        return null;
    }

    protected CustomEFDTNode newNode(int depth, DoubleVector classDistribution, List<Integer> usedNominalAttributes) {
        return new CustomEFDTNode(
                splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence,
                leafPrediction, minSamplesReevaluate, depth, maxDepth,
                tau, tauReevaluate, relMinDeltaG, binaryOnly, noPrePrune, nominalObserverBlueprint,
                classDistribution, usedNominalAttributes, -1  // we don't block attributes in EFDT
        );
    }

    private void updateObservers(Instance instance) {
        for (int i = 0; i < instance.numAttributes() - 1; i++) { //update likelihood
            int instAttIndex = modelAttIndexToInstanceAttIndex(i, instance);
            AttributeClassObserver obs = this.attributeObservers.get(i);
            if (obs == null) {
                obs = instance.attribute(instAttIndex).isNominal() ? newNominalClassObserver() : newNumericClassObserver();
                this.attributeObservers.set(i, obs);
            }
            obs.observeAttributeClass(instance.value(instAttIndex), (int) instance.classValue(), instance.weight());
        }
    }

    boolean isLeaf() {
        if (successors == null)
            return true;
        return successors.size() == 0;
    }

    boolean isPure() {
        return observedClassDistribution.numNonZeroEntries() < 2;
    }

    protected NominalAttributeClassObserver newNominalClassObserver() {
        return (NominalAttributeClassObserver) nominalObserverBlueprint.copy();
    }

    protected NumericAttributeClassObserver newNumericClassObserver() {
        return (NumericAttributeClassObserver) numericObserverBlueprint.copy();
    }

    protected List<Integer> getUsedNominalAttributesForSuccessor(Attribute splitAttribute, Integer splitAttributeIndex) {
        List<Integer> usedNomAttributesCpy = new ArrayList<>(usedNominalAttributes); //deep copy
        if (splitAttribute.isNominal())
            usedNomAttributesCpy.add(splitAttributeIndex);
        return usedNomAttributesCpy;
    }

    protected void updateInfogainSum(AttributeSplitSuggestion[] suggestions) {
        for (AttributeSplitSuggestion sugg : suggestions) {
            if (sugg.splitTest != null) {
                if (!infogainSum.containsKey((sugg.splitTest.getAttsTestDependsOn()[0]))) {
                    infogainSum.put((sugg.splitTest.getAttsTestDependsOn()[0]), 0.0);
                }
                double currentSum = infogainSum.get((sugg.splitTest.getAttsTestDependsOn()[0]));
                infogainSum.put((sugg.splitTest.getAttsTestDependsOn()[0]), currentSum + sugg.merit);
            } else { // handle the null attribute
                double currentSum = infogainSum.get(-1); // null split
                infogainSum.put(-1, Math.max(0.0, currentSum + sugg.merit));
                assert infogainSum.get(-1) >= 0.0 : "Negative infogain shouldn't be possible here.";
            }
        }
    }

    protected boolean shouldSplitLeaf(AttributeSplitSuggestion[] suggestions,
                                    double confidence,
                                    DoubleVector observedClassDistribution
    ) {
        boolean shouldSplit = false;
        if (suggestions.length < 2) {
            shouldSplit = suggestions.length > 0;
        } else {
            AttributeSplitSuggestion bestSuggestion = suggestions[suggestions.length - 1];

            double bestSuggestionAverageMerit = bestSuggestion.merit;
            double currentAverageMerit = 0.0;
            double eps = computeHoeffdingBound();

            shouldSplit = bestSuggestionAverageMerit - currentAverageMerit > eps || eps < tau;
            if (bestSuggestion.merit < 1e-10)
                shouldSplit = false; // we don't use average here

            if (shouldSplit) {  //TODO: Check why our approach degrades when we have this turned on.
                for (Integer i : usedNominalAttributes) {
                    if (bestSuggestion.splitTest.getAttsTestDependsOn()[0] == i) {
                        shouldSplit = false;
                        break;
                    }
                }
            }
        }
        return shouldSplit;
    }

    double getCurrentSuggestionAverageMerit(AttributeSplitSuggestion[] suggestions) {
        double merit = 0.0;
        if (splitTest != null) {
            if (splitTest instanceof NominalAttributeMultiwayTest) {
                for (AttributeSplitSuggestion s: suggestions) {
                    if (s.splitTest == null)
                        continue;
                    if (s.splitTest.getAttsTestDependsOn()[0] == splitAttributeIndex) {
                        merit = s.merit;
                        break;
                    }
                }
            }
            else if (splitTest instanceof NominalAttributeBinaryTest) {
                double currentValue = successors.getReferenceValue();
                NominalAttributeClassObserver obs = (NominalAttributeClassObserver) attributeObservers.get(splitAttributeIndex);
                AttributeSplitSuggestion xCurrent = obs.forceSplit(splitCriterion, observedClassDistribution.getArrayCopy(), splitAttributeIndex, true, currentValue);
                merit = xCurrent == null ? 0.0 : xCurrent.merit;
                if (xCurrent != null)
                    merit = xCurrent.splitTest == null ? 0.0 : xCurrent.merit;
            }
            else if (splitTest instanceof NumericAttributeBinaryTest) {
                double currentThreshold = successors.getReferenceValue();
                GaussianNumericAttributeClassObserver obs = (GaussianNumericAttributeClassObserver) attributeObservers.get(splitAttributeIndex);
                AttributeSplitSuggestion xCurrent = obs.forceSplit(splitCriterion, observedClassDistribution.getArrayCopy(), splitAttributeIndex, currentThreshold);
                merit = xCurrent == null ? 0.0 : xCurrent.merit;
                if (xCurrent != null)
                    merit = xCurrent.splitTest == null ? 0.0 : xCurrent.merit;
            }
        }
        return merit == Double.NEGATIVE_INFINITY ? 0.0 : merit;
    }

    double getSuggestionAverageMerit(InstanceConditionalTest splitTest) {
        double averageMerit;

        if (splitTest == null) {
            averageMerit = infogainSum.get(-1) / Math.max(numSplitAttempts, 1.0);
        } else {
            Integer key = splitTest.getAttsTestDependsOn()[0];
            if (!infogainSum.containsKey(key)) {
                infogainSum.put(key, 0.0);
            }
            averageMerit = infogainSum.get(key) / Math.max(numSplitAttempts, 1.0);
        }
        return averageMerit;
    }

    int argmax(double[] array) {
        double max = array[0];
        int maxarg = 0;

        for (int i = 1; i < array.length; i++) {

            if (array[i] > max) {
                max = array[i];
                maxarg = i;
            }
        }
        return maxarg;
    }

    public int getSubtreeDepth() {
        if (isLeaf())
            return depth;
        Set<Integer> succDepths = new HashSet<>();
        for (CustomEFDTNode successor: successors.getAllSuccessors()) {
            succDepths.add(successor.getSubtreeDepth());
        }
        return Collections.max(succDepths);
    }

    AttributeSplitSuggestion replaceBestSuggestionIfAttributeIsBlocked(AttributeSplitSuggestion bestSuggestion, AttributeSplitSuggestion[] suggestions, int blockedAttributeIndex) {
        if (suggestions.length == 0)
            return null;
        if (bestSuggestion.splitTest == null)
            return bestSuggestion;
        if (suggestions.length == 1)
            return bestSuggestion;
        if (bestSuggestion.splitTest.getAttsTestDependsOn()[0] == blockedAttributeIndex) {
            ArrayUtils.remove(suggestions, suggestions.length - 1);
            return suggestions[suggestions.length - 1];
        }
        return bestSuggestion;
    }

    protected boolean makeSplit(Attribute splitAttribute, AttributeSplitSuggestion suggestion) {
        boolean isNominal = splitAttribute.isNominal();
        boolean isBinary = !(suggestion.splitTest instanceof NominalAttributeMultiwayTest);
        Double splitValue = null;
        if (isNominal && isBinary)
            splitValue = ((NominalAttributeBinaryTest) suggestion.splitTest).getValue();
        else if (!isNominal)
            splitValue = ((NumericAttributeBinaryTest) suggestion.splitTest).getSplitValue();

        successors = new Successors(isBinary, !isNominal, splitValue);

        setSplitAttribute(suggestion, splitAttribute);
        return initializeSuccessors(suggestion, splitAttribute);
    }
}
