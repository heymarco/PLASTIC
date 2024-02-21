package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.GaussianNumericAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NullAttributeClassObserver;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.core.AutoExpandVector;
import moa.core.DoubleVector;
import org.netlib.arpack.Ssaitr;
import org.w3c.dom.Attr;
import scala.math.Numeric;

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
            NumericAttributeBinaryTest test = (NumericAttributeBinaryTest) splitTest;
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
            for (int i = 0; i < 1; i++) {
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
        if (isPure())
            return;

        numSplitAttempts++;

        AttributeSplitSuggestion[] bestSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSuggestions);
        if (bestSuggestions.length == 0)
            return;
        updateInfogainSum(bestSuggestions);

        //compute Hoeffding bound
        double eps = computeHoeffdingBound(
                splitCriterion.getRangeOfMerit(observedClassDistribution.getArrayCopy()),
                currentConfidence(),
                nodeTime
        );

        // get best split suggestions
        AttributeSplitSuggestion[] bestSplitSuggestions = getBestSplitSuggestions(splitCriterion);
        Arrays.sort(bestSplitSuggestions);

        // get the best suggestion
        AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];


        for (AttributeSplitSuggestion bestSplitSuggestion : bestSplitSuggestions) {

            if (bestSplitSuggestion.splitTest != null) {
                if (!infogainSum.containsKey((bestSplitSuggestion.splitTest.getAttsTestDependsOn()[0]))) {
                    infogainSum.put((bestSplitSuggestion.splitTest.getAttsTestDependsOn()[0]), 0.0);
                }
                double currentSum = infogainSum.get((bestSplitSuggestion.splitTest.getAttsTestDependsOn()[0]));
                infogainSum.put((bestSplitSuggestion.splitTest.getAttsTestDependsOn()[0]), currentSum + bestSplitSuggestion.merit);
            } else { // handle the null attribute. this is fine to do- it'll always average zero, and we will use this later to potentially burn bad splits.
                double currentSum = infogainSum.get(-1); // null split
                infogainSum.put(-1, currentSum + bestSplitSuggestion.merit);
            }

        }

        // get the average merit for best and current splits

        double bestSuggestionAverageMerit;
        double currentAverageMerit;

        if (bestSuggestion.splitTest == null) { // best is null
            bestSuggestionAverageMerit = 0.0; // infogainSum.get(-1) / numSplitAttempts;
        } else {
            bestSuggestionAverageMerit = bestSuggestion.merit;  // infogainSum.get(bestSuggestion.splitTest.getAttsTestDependsOn()[0]) / numSplitAttempts;
        }


        currentAverageMerit = getCurrentSuggestionAverageMerit(bestSuggestions);

        double deltaG = bestSuggestionAverageMerit - currentAverageMerit;

        if (deltaG > eps || (eps < tauReevaluate && deltaG > tauReevaluate * relMinDeltaG)) {
//            System.err.println(nodeTime);

            if (bestSuggestion.splitTest == null) {
                System.out.println("preprune - null wins");
                killSubtree();
                resetSplitAttribute();
                return;
            }

            Attribute newSplitAttribute = instance.attribute(bestSuggestion.splitTest.getAttsTestDependsOn()[0]);
            boolean success = false;
            if (maxBranchLength > 1)
                success = performReordering(bestSuggestion, newSplitAttribute);
            setSplitAttribute(bestSuggestion, newSplitAttribute);
            if (!success) {
                initializeSuccessors(bestSuggestion, newSplitAttribute);
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

    void resetInfogainTracking() {
        infogainSum.replaceAll((k, v) -> 0.0);
        numSplitAttempts = 0;
    }
}
