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
import org.w3c.dom.Attr;

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
            SplitCriterion splitCriterion, int gracePeriod, Double confidence, Double adaptiveConfidence, boolean useAdaptiveConfidence, boolean disableBlockParentSplitAttribute, String leafPrediction, Integer minSamplesReevaluate, Integer depth, Integer maxDepth, Double tau, Double tauReevaluate, Double relMinDeltaG, boolean binaryOnly, boolean noPrePrune, NominalAttributeClassObserver nominalObserverBlueprint, DoubleVector observedClassDistribution, List<Integer> usedNominalAttributes,
            int maxBranchLength, double acceptedNumericThresholdDeviation, Integer blockedAttributeIndex
    ) {
        super(splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence, disableBlockParentSplitAttribute, leafPrediction, minSamplesReevaluate, depth, maxDepth, tau, tauReevaluate, relMinDeltaG, binaryOnly, noPrePrune, nominalObserverBlueprint, observedClassDistribution, usedNominalAttributes, blockedAttributeIndex);
        this.maxBranchLength = maxBranchLength;
        this.acceptedNumericThresholdDeviation = acceptedNumericThresholdDeviation;
        restructurer = new Restructurer(maxBranchLength, acceptedNumericThresholdDeviation);
    }

    public PlasticNode(PlasticNode other) {
        super((SplitCriterion) other.splitCriterion.copy(), other.gracePeriod, other.confidence, other.adaptiveConfidence, other.useAdaptiveConfidence, other.disableBlockParentSplitAttribute,
                other.leafPrediction, other.minSamplesReevaluate, other.depth, other.maxDepth,
                other.tau, other.tauReevaluate, other.relMinDeltaG, other.binaryOnly, other.noPrePrune, other.nominalObserverBlueprint,
                (DoubleVector) other.observedClassDistribution.copy(), other.usedNominalAttributes, other.blockedAttributeIndex);
        this.acceptedNumericThresholdDeviation = other.acceptedNumericThresholdDeviation;
        this.maxBranchLength = other.maxBranchLength;
        if (other.successors != null)
            this.successors = new Successors(other.successors, true);
        this.splitTest = (InstanceConditionalTest) other.splitTest.copy();
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
    }

    public boolean isDummy() {
        return isDummy;
    }

    @Override
    protected PlasticNode newNode(int depth, DoubleVector classDistribution, List<Integer> usedNominalAttributes) {
        return new PlasticNode(
                splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence, disableBlockParentSplitAttribute,
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
        this.splitTest = splitTest;
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
        for (int i = 0; i < attributeObservers.size(); i++) {
            AttributeClassObserver obs = attributeObservers.get(i).getClass() == NominalAttributeClassObserver.class ? newNominalClassObserver() : newNumericClassObserver();
            this.attributeObservers.set(i, obs);
        }
    }

    public boolean isArtificial() {
        return isArtificial;
    }

    public void setIsArtificial() {
        isArtificial = true;
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
            setSplitAttribute(suggestion, splitAttribute);
            success = initializeSuccessors(suggestion, splitAttribute);
        }
        else {
            GaussianNumericAttributeClassObserver numericObserver = (GaussianNumericAttributeClassObserver) observer;
            AttributeSplitSuggestion suggestion = numericObserver.forceSplit(
                    splitCriterion, observedClassDistribution.getArrayCopy(), splitAttributeIndex, splitValue
            );
            assert suggestion != null;
            setSplitAttribute(suggestion, splitAttribute);
            success = initializeSuccessors(suggestion, splitAttribute);
        }

        if (!success) {
            for (int i = 0; i < 2; i++) {
                Double usedSplitVal = splitValue != null ? splitValue : splitAttribute.indexOfValue(splitAttribute.value(i));
                PlasticNode dummyNode = newNode(depth, new DoubleVector(), getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex));
                SuccessorIdentifier dummyKey = new SuccessorIdentifier(splitAttribute.isNumeric(), usedSplitVal, usedSplitVal, i == 0);
                success = successors.addSuccessor(dummyNode, dummyKey);  // will be pruned later on.
                dummyNode.isDummy = true;
                if (!success)
                    break;
            }
        }

        return success;
    }

    @Override
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
        double bestMerit = getSuggestionAverageMerit(xBest.splitTest);
        double currentMerit = getCurrentSuggestionAverageMerit(bestSuggestions);
        double deltaG = bestMerit - currentMerit;

        if (deltaG > eps || (eps < tauReevaluate && deltaG > tauReevaluate * relMinDeltaG)) {
//            System.err.println(nodeTime);
            if (xBest.splitTest == null) {
                System.out.println("preprune - null wins");
                killSubtree();
                resetSplitAttribute();
            }
            else {
//                boolean doResplit = true;
//                if (splitTest == xBest.splitTest && splitTest.getClass() == NumericAttributeBinaryTest.class) {
//                    Collection<CustomEFDTNode> successorNodes = successors.getAllSuccessors();
//                    for (CustomEFDTNode successor : successorNodes) {
//                        if (argmax(xBest.resultingClassDistributions[0]) == argmax(successor.getObservedClassDistribution())) {
//                            NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) xBest.splitTest;
//                            successors.adjustThreshold(test.getSplitValue());
//                            splitTest = xBest.splitTest;
//                            doResplit = false;
//                            break;
//                        }
//                    }
//                }
                boolean success = false;
                Attribute newSplitAttribute = instance.attribute(xBest.splitTest.getAttsTestDependsOn()[0]);
                if (maxBranchLength > 1)
                    success = performReordering(xBest, newSplitAttribute);
                setSplitAttribute(xBest, newSplitAttribute);
                if (!success) {
                    initializeSuccessors(xBest, newSplitAttribute);
                }
            }
        }
    }

    private boolean performReordering(AttributeSplitSuggestion xBest, Attribute splitAttribute) {
        Double splitValue = null;
        InstanceConditionalTest test = xBest.splitTest;
        if (test instanceof NominalAttributeBinaryTest)
            splitValue = ((NominalAttributeBinaryTest) test).getValue();
        else if (test instanceof NumericAttributeBinaryTest)
            splitValue = ((NumericAttributeBinaryTest) test).getValue();

        PlasticNode restructuredNode = restructurer.restructure(new PlasticNode(this), xBest, splitAttribute, splitValue);

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

    void resetInfogainTracking() {
        infogainSum.replaceAll((k, v) -> 0.0);
        numSplitAttempts = 0;
    }
}
