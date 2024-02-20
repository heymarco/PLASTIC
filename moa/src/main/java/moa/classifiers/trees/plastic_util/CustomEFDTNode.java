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
import moa.classifiers.trees.EFDT;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;

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
    protected HashMap<Integer, Double> infogainSum;
    protected InstanceConditionalTest splitTest;
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
    protected Integer blockedAttributeIndex;
    protected final boolean disableBlockParentSplitAttribute;
    private boolean isInitialized = false;

    public CustomEFDTNode(SplitCriterion splitCriterion,
                          int gracePeriod,
                          Double confidence,
                          Double adaptiveConfidence,
                          boolean useAdaptiveConfidence,
                          boolean disableBlockParentSplitAttribute,
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
                          Integer blockedAttributeIndex) {
        this.gracePeriod = gracePeriod;
        this.splitCriterion = splitCriterion;
        this.confidence = confidence;
        this.adaptiveConfidence = adaptiveConfidence;
        this.useAdaptiveConfidence = useAdaptiveConfidence;
        this.disableBlockParentSplitAttribute = disableBlockParentSplitAttribute;
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
        this.blockedAttributeIndex = blockedAttributeIndex;
        this.nominalObserverBlueprint = nominalObserverBlueprint;
        this.observedClassDistribution = observedClassDistribution != null ? observedClassDistribution : new DoubleVector();
        this.infogainSum = new HashMap<>();
        this.infogainSum.put(-1, 0.0); // Initialize for null split
        classDistributionAtTimeOfCreation = new DoubleVector(this.observedClassDistribution.getArrayCopy());
    }

    public static double computeHoeffdingBound(double range, double confidence, double n) {
        return Math.sqrt(((range * range) * Math.log(1.0 / confidence))
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
            AttributeSplitSuggestion bestSuggestion = getSuggestionForAttributeIndex(i);
            if (bestSuggestion != null) {
                bestSuggestions.add(bestSuggestion);
            }
        }
        return bestSuggestions.toArray(new AttributeSplitSuggestion[bestSuggestions.size()]);
    }

    public AttributeSplitSuggestion getSuggestionForAttributeIndex(int i) {
        AttributeClassObserver obs = attributeObservers.get(i);
        double[] preSplitDist = observedClassDistribution.getArrayCopy();
        if (obs != null) {
            AttributeSplitSuggestion bestSuggestion = obs.getBestEvaluatedSplitSuggestion(
                    splitCriterion, preSplitDist, i, binaryOnly
            );
            return bestSuggestion;
        }
        return null;
    }

    public void learnInstance(Instance instance, int totalNumInstances) {
        seenWeight += instance.weight();
        nodeTime++;

        updateStatistics(instance);
        updateObservers(instance);

        if (isLeaf() && nodeTime % gracePeriod == 0) {
            attemptInitialSplit(instance);
        }
        else if (!isLeaf() && totalNumInstances % minSamplesReevaluate == 0) {
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

        numSplitAttempts += 1;

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSuggestions);
        AttributeSplitSuggestion xBest = bestSuggestions[bestSuggestions.length - 1];
        updateInfogainSum(bestSuggestions);

//        if (blockedAttributeIndex != null && !disableBlockParentSplitAttribute) {
//            if (xBest.splitTest.getAttsTestDependsOn()[0] == blockedAttributeIndex) {
//                if (bestSuggestions.length > 1) {
//                    // don't use the blocked attribute for initial split. Use second best instead.
//                    xBest = bestSuggestions[bestSuggestions.length - 2];
//                }
//            }
//        }

        if (!shouldSplitLeaf(bestSuggestions))
            return;

        if (xBest.splitTest == null) {
            // preprune - null wins
            System.out.println("preprune - null wins");
            killSubtree();
            resetSplitAttribute();
            return;
        }
        int newSplitAttributeIndex = xBest.splitTest.getAttsTestDependsOn()[0];
        Attribute newSplitAttribute = instance.attribute(modelAttIndexToInstanceAttIndex(newSplitAttributeIndex, instance));
        setSplitAttribute(xBest, newSplitAttribute, newSplitAttributeIndex);
        initializeSuccessors(xBest, splitAttribute);
    }

    protected void reevaluateSplit(Instance instance) {
        numSplitAttempts++;

        double eps = computeHoeffdingBound(
                splitCriterion.getRangeOfMerit(observedClassDistribution.getArrayRef()),
                currentConfidence(),
                nodeTime
//                observedClassDistribution.sumOfValues()
        );

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        updateInfogainSum(bestSuggestions);
        Arrays.sort(bestSuggestions);

        if (bestSuggestions.length == 0)
            return;
        AttributeSplitSuggestion xBest = bestSuggestions[bestSuggestions.length - 1];

        double bestMerit;
        if (xBest.splitTest == null) { // best is null
            bestMerit = infogainSum.get(-1) / numSplitAttempts;
        } else {
            bestMerit = infogainSum.get(xBest.splitTest.getAttsTestDependsOn()[0]) / numSplitAttempts;
        }

        double currentAverageMerit;
        if (splitTest == null) { // current is null- shouldn't happen, check for robustness
            currentAverageMerit = infogainSum.get(-1) / numSplitAttempts;
        } else {
            currentAverageMerit = infogainSum.get(splitTest.getAttsTestDependsOn()[0]) / numSplitAttempts;
        }

//        double currentMerit = getCurrentSuggestionAverageMerit(bestSuggestions);
        double deltaG = bestMerit - currentAverageMerit;

        if (deltaG > eps || (eps < tauReevaluate && deltaG > tauReevaluate * relMinDeltaG)) {

            if (xBest.splitTest == null) {
                System.out.println("preprune - null wins");
                killSubtree();
                resetSplitAttribute();
            } else {
                boolean doResplit = true;
                if (splitTest == xBest.splitTest && splitTest.getClass() == NumericAttributeBinaryTest.class) {
                    Collection<CustomEFDTNode> successorNodes = successors.getAllSuccessors();
                    for (CustomEFDTNode successor : successorNodes) {
                        if (argmax(xBest.resultingClassDistributions[0]) == argmax(successor.getObservedClassDistribution())) {
                            NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) xBest.splitTest;
                            successors.adjustThreshold(test.getSplitValue());
                            splitTest = xBest.splitTest;
                            doResplit = false;
                            break;
                        }
                    }
                }
                if (doResplit) {
                    int newSplitAttributeIndex = xBest.splitTest.getAttsTestDependsOn()[0];
                    Attribute newSplitAttribute = instance.attribute(modelAttIndexToInstanceAttIndex(newSplitAttributeIndex, instance));
                    setSplitAttribute(xBest, newSplitAttribute, newSplitAttributeIndex);
                    initializeSuccessors(xBest, splitAttribute);
                }
            }
        }
    }

    protected boolean initializeSuccessors(AttributeSplitSuggestion xBest, Attribute splitAttribute) {

        boolean isBinary = xBest.splitTest.getClass() == NominalAttributeBinaryTest.class || xBest.splitTest.getClass() == NumericAttributeBinaryTest.class;
        boolean isNominal = xBest.splitTest.getClass() != NumericAttributeBinaryTest.class;

        Double splitValue = xBest.splitTest.getClass() == NumericAttributeBinaryTest.class ? ((NumericAttributeBinaryTest) xBest.splitTest).getSplitValue() : null;
        if (xBest.splitTest.getClass() == NumericAttributeBinaryTest.class) {
            splitValue = ((NumericAttributeBinaryTest) xBest.splitTest).getSplitValue();
        }
        else if (xBest.splitTest.getClass() == NominalAttributeBinaryTest.class) {
            splitValue = ((NominalAttributeBinaryTest) xBest.splitTest).getValue();
        }

        successors = new Successors(isBinary, !isNominal, splitValue);

        Integer splitAttributeIndex = xBest.splitTest.getAttsTestDependsOn()[0];
        for (int i = 0; i < xBest.numSplits(); i++) {
            double[] j = xBest.resultingClassDistributionFromSplit(i);

            if (xBest.splitTest.getClass() == NumericAttributeBinaryTest.class) {
                CustomEFDTNode newChild = newNode(depth + 1, new DoubleVector(j), new ArrayList<>(usedNominalAttributes));
                successors.addSuccessorNumeric(splitValue, newChild, i == 0);
            }

            else if (xBest.splitTest.getClass() == NominalAttributeBinaryTest.class) {
                CustomEFDTNode newChild = newNode(depth + 1, new DoubleVector(j), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                if (i == 0)
                    successors.addSuccessorNominalBinary(splitValue, newChild);
                else
                    successors.addDefaultSuccessorNominalBinary(newChild);
            }

            else if (xBest.splitTest.getClass() == NominalAttributeMultiwayTest.class) {
                CustomEFDTNode newChild = newNode(depth + 1, new DoubleVector(j), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                successors.addSuccessorNominalMultiway((double) i, newChild);
            }

            else {
                // this should never happen. check for robustness.
                System.err.println("Error during initializeSuccessors");
            }
        }
        return successors.size() == xBest.numSplits();
    }

    protected void setSplitAttribute(AttributeSplitSuggestion xBest, Attribute splitAttribute, int splitAttributeIndex) {
        this.splitAttribute = splitAttribute;
        this.splitAttributeIndex = splitAttributeIndex;
        splitTest = (InstanceConditionalTest) xBest.splitTest.copy();
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
        Double attValue = instance.value(modelAttIndexToInstanceAttIndex(splitAttributeIndex, instance));
        CustomEFDTNode successor = successors.getSuccessorNode(attValue);
        if (successor == null)
            successor = addSuccessor(instance);
        if (successor != null)
        successor.learnInstance(instance, totalNumInstances);
    }

    protected CustomEFDTNode addSuccessor(Instance instance) {
        List<Integer> usedNomAttributes = new ArrayList<>(usedNominalAttributes); //deep copy
//        if (splitAttribute.isNominal())
//            usedNominalAttributes.add(splitAttributeIndex);
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
                boolean success = successors.addSuccessorNumeric(successors.getReferenceValue(), successor, true);
                return success ? successor : null;
            } else if (successors.upperIsMissing()) {
                boolean success = successors.addSuccessorNumeric(successors.getReferenceValue(), successor, false);
                return success ? successor : null;
            }
        }
        return null;
    }

    protected CustomEFDTNode newNode(int depth, DoubleVector classDistribution, List<Integer> usedNominalAttributes) {
        return new CustomEFDTNode(
                splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence, disableBlockParentSplitAttribute,
                leafPrediction, minSamplesReevaluate, depth, maxDepth,
                tau, tauReevaluate, relMinDeltaG, binaryOnly, noPrePrune, nominalObserverBlueprint,
                classDistribution, usedNominalAttributes, splitAttributeIndex
        );
    }

    private void updateObservers(Instance instance) {
        for (int i = 0; i < instance.numInputAttributes(); i++) { //update likelihood
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
        return new NominalAttributeClassObserver();
    }

    protected NumericAttributeClassObserver newNumericClassObserver() {
        return new GaussianNumericAttributeClassObserver();
    }

    protected List<Integer> getUsedNominalAttributesForSuccessor(Attribute splitAttribute, Integer splitAttributeIndex) {
        List<Integer> usedNomAttributesCpy = new ArrayList<>(usedNominalAttributes); //deep copy
        if (splitAttribute.isNominal())
            usedNomAttributesCpy.add(splitAttributeIndex);
        return usedNomAttributesCpy;
    }

    void updateInfogainSum(AttributeSplitSuggestion[] suggestions) {
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

    private boolean shouldSplitLeaf(AttributeSplitSuggestion[] suggestions
    ) {
        boolean shouldSplit = false;
        if (suggestions.length < 2) {
            shouldSplit = suggestions.length > 0;
        } else {
            double hoeffdingBound = computeHoeffdingBound(
                    splitCriterion.getRangeOfMerit(observedClassDistribution.getArrayRef()),
                    confidence,
                    nodeTime
//                    observedClassDistribution.sumOfValues()
            );
            AttributeSplitSuggestion bestSuggestion = suggestions[suggestions.length - 1];

            double bestSuggestionAverageMerit;
            double currentAverageMerit = infogainSum.get(-1) / numSplitAttempts;

            // because this is an unsplit leaf. current average merit should be always zero on the null split.

            if (bestSuggestion.splitTest == null) { // if you have a null split
                bestSuggestionAverageMerit = infogainSum.get(-1) / numSplitAttempts;
            } else {
                bestSuggestionAverageMerit = infogainSum.get((bestSuggestion.splitTest.getAttsTestDependsOn()[0])) / numSplitAttempts;
            }

            if (bestSuggestion.merit < 1e-10) {
                shouldSplit = false; // we don't use average here
            } else if ((bestSuggestionAverageMerit - currentAverageMerit) >
                    hoeffdingBound
                    || (hoeffdingBound < tau)) {
                if (bestSuggestionAverageMerit - currentAverageMerit < hoeffdingBound) {
                    // Placeholder to list this possibility
                }
                shouldSplit = true;
            }
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
        if (splitTest != null) {
            if (splitTest instanceof NominalAttributeMultiwayTest) {
                for (AttributeSplitSuggestion s: suggestions) {
                    if (s.splitTest == null)
                        continue;
                    if (s.splitTest.getAttsTestDependsOn()[0] == splitAttributeIndex)
                        return getSuggestionAverageMerit(s.splitTest);
                }
            }
        }
        return getSuggestionAverageMerit(splitTest);
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

    protected void resetStatisticsAfterSplit() {
        numSplitAttempts = 0;
        classDistributionAtTimeOfCreation = new DoubleVector(classDistributionAtTimeOfCreation.getArrayCopy());
        nodeTime = 0;
    }
}
