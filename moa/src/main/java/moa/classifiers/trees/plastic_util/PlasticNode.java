package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.GaussianNumericAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;

import java.util.*;

public class PlasticNode extends CustomEFDTNode {

    private static final long serialVersionUID = 4L;

    private Set<Attribute> childrenSplitAttributes;
    private boolean nodeGotRestructured = false;
    private boolean isArtificial = false;
    private final Restructurer restructurer;
    private final int maxBranchLength;
    private final double acceptedNumericThresholdDeviation;
    private boolean isDummy = false;

    protected void setRestructuredFlag() {
        nodeGotRestructured = true;
    }

    protected void resetRestructuredFlag() {
        nodeGotRestructured = false;
    }

    protected boolean getRestructuredFlag() {
        return nodeGotRestructured;
    }

    public PlasticNode(
            SplitCriterion splitCriterion, int gracePeriod, Double confidence, Double adaptiveConfidence, boolean useAdaptiveConfidence, String leafPrediction, Integer minSamplesReevaluate, Integer depth, Integer maxDepth, Double tau, Double tauReevaluate, Double relMinDeltaG, boolean binaryOnly, boolean noPrePrune, NominalAttributeClassObserver nominalObserverBlueprint, DoubleVector observedClassDistribution, List<Integer> usedNominalAttributes,
            int maxBranchLength, double acceptedNumericThresholdDeviation, int blockedAttributeIndex
    ) {
        super(splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence, leafPrediction,
                minSamplesReevaluate, depth, maxDepth, tau, tauReevaluate, relMinDeltaG, binaryOnly, noPrePrune,
                nominalObserverBlueprint, observedClassDistribution, usedNominalAttributes, blockedAttributeIndex);
        this.maxBranchLength = maxBranchLength;
        this.acceptedNumericThresholdDeviation = acceptedNumericThresholdDeviation;
        restructurer = new Restructurer(maxBranchLength, acceptedNumericThresholdDeviation);
    }

    public PlasticNode(PlasticNode other) {
        super((SplitCriterion) other.splitCriterion.copy(), other.gracePeriod, other.confidence, other.adaptiveConfidence, other.useAdaptiveConfidence,
                other.leafPrediction, other.minSamplesReevaluate, other.depth, other.maxDepth,
                other.tau, other.tauReevaluate, other.relMinDeltaG, other.binaryOnly, other.noPrePrune, other.nominalObserverBlueprint,
                (DoubleVector) other.observedClassDistribution.copy(), other.usedNominalAttributes, other.blockedAttributeIndex);
        this.acceptedNumericThresholdDeviation = other.acceptedNumericThresholdDeviation;
        this.maxBranchLength = other.maxBranchLength;
        if (other.successors != null)
            this.successors = new Successors(other.successors, true);
        if (other.getSplitTest() != null)
            setSplitTest((InstanceConditionalTest) other.getSplitTest().copy());
        this.infogainSum = new HashMap<>(infogainSum);
        this.numSplitAttempts = other.numSplitAttempts;
        this.classDistributionAtTimeOfCreation = other.classDistributionAtTimeOfCreation;
        this.nodeTime = other.nodeTime;
        this.splitAttribute = other.splitAttribute;
        this.splitAttributeIndex = other.splitAttributeIndex;
        this.seenWeight = other.seenWeight;
        this.isArtificial = other.isArtificial;
        if (other.attributeObservers != null)
            this.attributeObservers = (AutoExpandVector<AttributeClassObserver>) other.attributeObservers.copy();
        restructurer = other.restructurer;
        blockedAttributeIndex = other.blockedAttributeIndex;
    }

    public boolean isDummy() {
        return isDummy;
    }

    @Override
    protected PlasticNode addSuccessor(Instance instance) {
        List<Integer> usedNomAttributes = new ArrayList<>(usedNominalAttributes); //deep copy
//        if (splitAttribute.isNominal()) //TODO: I don't know what this does here...
//            usedNominalAttributes.add(splitAttributeIndex);
        PlasticNode successor = newNode(depth + 1, new DoubleVector(), usedNomAttributes);
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
            NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) getSplitTest();
            if (successors.lowerIsMissing()) {
                boolean success = successors.addSuccessorNumeric(test.getValue(), successor, true);
                return success ? successor : null;
            } else if (successors.upperIsMissing()) {
                boolean success = successors.addSuccessorNumeric(test.getValue(), successor, false);
                return success ? successor : null;
            }
        }
        return null;
    }

    @Override
    protected PlasticNode newNode(int depth, DoubleVector classDistribution, List<Integer> usedNominalAttributes) {
        return new PlasticNode(
                splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence,
                leafPrediction, minSamplesReevaluate, depth, maxDepth,
                tau, tauReevaluate, relMinDeltaG, binaryOnly, noPrePrune, nominalObserverBlueprint,
                classDistribution, usedNominalAttributes, maxBranchLength, acceptedNumericThresholdDeviation, splitAttributeIndex
        );
    }

    public Set<Attribute> collectChildrenSplitAttributes() {
        childrenSplitAttributes = new HashSet<>();
        if (isLeaf()) {
            // we have no split attribute
            return childrenSplitAttributes;
        }
        // add the split attribute of this node to the  set
        childrenSplitAttributes.add(splitAttribute);
        for (CustomEFDTNode successor: successors.getAllSuccessors()) {
            // add the split attributes of the subtree
            childrenSplitAttributes.addAll(((PlasticNode) successor).collectChildrenSplitAttributes());
        }
        return childrenSplitAttributes;
    }

    public Set<Attribute> getChildrenSplitAttributes() {
        return childrenSplitAttributes;
    }

    public void transferSplit(Successors successors,
                              Attribute splitAttribute,
                              int splitAttributeIndex,
                              InstanceConditionalTest splitTest) {
        this.successors = successors;
        this.splitAttribute = splitAttribute;
        this.splitAttributeIndex = splitAttributeIndex;
        setSplitTest(splitTest);
    }

    protected void incrementDepthInSubtree() {
        depth++;
        if (isLeaf())
            return;
        for (SuccessorIdentifier key: successors.getKeyset()) {
            PlasticNode successor = (PlasticNode) successors.getSuccessorNode(key);
            successor.incrementDepthInSubtree();
        }
    }

    protected void resetObservers() {
        AutoExpandVector<AttributeClassObserver> newObservers = new AutoExpandVector<>();
        for (AttributeClassObserver observer: attributeObservers) {
            if (observer.getClass() == nominalObserverBlueprint.getClass())
                newObservers.add(newNominalClassObserver());
            else
                newObservers.add(newNumericClassObserver());
        }
        attributeObservers = newObservers;
    }

    public boolean isArtificial() {
        return isArtificial;
    }

    public void setIsArtificial() {
        isArtificial = true;
    }
    public void setIsArtificial(boolean val) {
        isArtificial = val;
    }

    protected boolean forceSplit(Attribute splitAttribute, int splitAttributeIndex, Double splitValue, boolean isBinary) {
        AttributeClassObserver observer = attributeObservers.get(splitAttributeIndex);
        if (observer == null) {
            observer = splitAttribute.isNominal() ? newNominalClassObserver() : newNumericClassObserver();
            this.attributeObservers.set(splitAttributeIndex, observer);
        }

        boolean success;
        if (splitAttribute.isNominal()) {
            NominalAttributeClassObserver nominalObserver = (NominalAttributeClassObserver) observer;
            AttributeSplitSuggestion suggestion = nominalObserver.forceSplit(
                    splitCriterion, observedClassDistribution.getArrayCopy(), splitAttributeIndex, isBinary, splitValue
            );
            if (suggestion != null) {
                success = makeSplit(splitAttribute, suggestion);
            }
            else
                success = false;

            if (!success) {
                if (successors == null)
                    successors = new Successors(isBinary, splitAttribute.isNumeric(), splitValue);
                if (this.splitAttribute != splitAttribute) {
                    this.splitAttribute = splitAttribute;
                    this.splitAttributeIndex = splitAttributeIndex;
                    setSplitTest(suggestion == null ? null : suggestion.splitTest);
                }

                if (!isBinary) {
                    PlasticNode dummyNode = newNode(depth, new DoubleVector(), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                    SuccessorIdentifier dummyKey = new SuccessorIdentifier(splitAttribute.isNumeric(), 0.0, 0.0, false);
                    success = successors.addSuccessor(dummyNode, dummyKey);  // will be pruned later on.
                    dummyNode.isDummy = true;
                    return success;
                }
                else {
                    PlasticNode a = newNode(depth + 1, new DoubleVector(), new LinkedList<>(usedNominalAttributes));
                    PlasticNode b = newNode(depth + 1, new DoubleVector(), new LinkedList<>(usedNominalAttributes));
                    SuccessorIdentifier keyA = new SuccessorIdentifier(splitAttribute.isNumeric(), splitValue, splitValue, false);
                    successors.addSuccessor(a, keyA);
                    successors.addDefaultSuccessorNominalBinary(b);
                    return true;
                }
            }
        }

        else {
            GaussianNumericAttributeClassObserver numericObserver = (GaussianNumericAttributeClassObserver) observer;
            AttributeSplitSuggestion suggestion = numericObserver.forceSplit(
                    splitCriterion, observedClassDistribution.getArrayCopy(), splitAttributeIndex, splitValue
            );
            assert suggestion != null;
            success = makeSplit(splitAttribute, suggestion);

            if (!success) {
                if (successors == null)
                    successors = new Successors(isBinary, splitAttribute.isNumeric(), splitValue);
                if (this.splitAttribute != splitAttribute) {
                    this.splitAttribute = splitAttribute;
                    this.splitAttributeIndex = splitAttributeIndex;
                }
                for (int i = 0; i < 1; i++) {
                    PlasticNode dummyNode = newNode(depth, new DoubleVector(), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                    SuccessorIdentifier dummyKey = new SuccessorIdentifier(splitAttribute.isNumeric(), splitValue, splitValue, i == 0);
                    success = successors.addSuccessor(dummyNode, dummyKey);  // will be pruned later on.
                    dummyNode.isDummy = true;
                    if (!success)
                        break;
                }
            }
        }

        return success;
    }

    @Override
    protected void reevaluateSplit(Instance instance) {
        if (isPure())
            return;

        numSplitAttempts++;

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSuggestions);
        if (bestSuggestions.length == 0)
            return;
        updateInfogainSum(bestSuggestions);

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
                return;
            }

            boolean doResplit = true;
//            if (
//                    splitTest instanceof NumericAttributeBinaryTest
//                            && splitTest.getAttsTestDependsOn() == bestSuggestion.splitTest.getAttsTestDependsOn()
//            ) {
//                Set<SuccessorIdentifier> keys = successors.getKeyset();
//                for (SuccessorIdentifier key: keys) {
//                    if (key.isLower()) {
//                        if (argmax(bestSuggestion.resultingClassDistributions[0]) == argmax(successors.getSuccessorNode(key).observedClassDistribution.getArrayRef())) {
//                            doResplit = false;
//                            break;
//                        }
//                    }
//                    else {
//                        if (argmax(bestSuggestion.resultingClassDistributions[1]) == argmax(successors.getSuccessorNode(key).observedClassDistribution.getArrayRef())) {
//                            doResplit = false;
//                            break;
//                        }
//                    }
//                }
//            }
//            if (!doResplit) {
//                NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) bestSuggestion.splitTest;
//                successors.adjustThreshold(test.getSplitValue());
//                splitTest = bestSuggestion.splitTest;
//                return;
//            }
            Attribute newSplitAttribute = instance.attribute(bestSuggestion.splitTest.getAttsTestDependsOn()[0]);
            boolean success = false;
            if (maxBranchLength > 1) {
                success = performReordering(bestSuggestion, newSplitAttribute);
                if (success)
                    setSplitAttribute(bestSuggestion, newSplitAttribute);
            }
            if (!success) {
                makeSplit(newSplitAttribute, bestSuggestion);
            }
            nodeTime = 0;
            seenWeight = 0.0;
        }
    }

    private boolean performReordering(AttributeSplitSuggestion xBest, Attribute splitAttribute) {
        Double splitValue = null;
        InstanceConditionalTest test = xBest.splitTest;
        if (test instanceof NominalAttributeBinaryTest)
            splitValue = ((NominalAttributeBinaryTest) test).getValue();
        else if (test instanceof NumericAttributeBinaryTest)
            splitValue = ((NumericAttributeBinaryTest) test).getValue();

        PlasticNode restructuredNode = restructurer.restructure(this, xBest, splitAttribute, splitValue);

        if (restructuredNode != null)
            successors = restructuredNode.getSuccessors();

        return restructuredNode != null;
    }

    protected void updateUsedNominalAttributesInSubtree(Attribute splitAttribute, Integer splitAttributeIndex) {
        if (isLeaf())
            return;
        for (CustomEFDTNode successor: successors.getAllSuccessors()) {
            PlasticNode s = (PlasticNode) successor;
            s.usedNominalAttributes = getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex);
            s.updateUsedNominalAttributesInSubtree(splitAttribute, splitAttributeIndex);
        }
    }

    protected Set<Double> getMajorityVotesOfLeaves() {
        Set<Double> majorityVotes = new HashSet<>();
        if (isLeaf()) {
            if (observedClassDistribution.numValues() == 0)
                return majorityVotes;
            majorityVotes.add((double) argmax(observedClassDistribution.getArrayRef()));
            return majorityVotes;
        }
        for (CustomEFDTNode s: getSuccessors().getAllSuccessors()) {
            majorityVotes.addAll(((PlasticNode) s).getMajorityVotesOfLeaves());
        }
        return majorityVotes;
    }
}
