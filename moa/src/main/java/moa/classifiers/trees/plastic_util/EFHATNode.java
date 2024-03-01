package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.driftdetection.ADWINChangeDetector;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.core.DoubleVector;

import java.util.LinkedList;
import java.util.List;

public class EFHATNode extends CustomEFDTNode {

    private CustomADWINChangeDetector changeDetector;  // we need to access the width of adwin to compute switch significance. This is not possible with the default adwin change detector class.
    private EFHATNode backgroundLearner;
    private LinkedList<Double> predictions = new LinkedList<>();

    public EFHATNode(SplitCriterion splitCriterion,
                     int gracePeriod,
                     Double confidence,
                     Double adaptiveConfidence,
                     boolean useAdaptiveConfidence,
                     String leafPrediction,
                     Integer minSamplesReevaluate,
                     Integer depth,
                     Integer maxDepth,
                     Double tau,
                     boolean binaryOnly,
                     boolean noPrePrune,
                     NominalAttributeClassObserver nominalObserverBlueprint,
                     DoubleVector observedClassDistribution,
                     List<Integer> usedNominalAttributes,
                     int blockedAttributeIndex,
                     CustomADWINChangeDetector changeDetector) {
        super(splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence, leafPrediction,
                minSamplesReevaluate, depth, maxDepth, tau, 0.0, 0.0, binaryOnly, noPrePrune,
                nominalObserverBlueprint, observedClassDistribution, usedNominalAttributes, blockedAttributeIndex);
        this.changeDetector = changeDetector == null ? new CustomADWINChangeDetector() : changeDetector;
    }

    @Override
    public double[] predict(Instance instance) {
        double[] pred = super.predict(instance);
        if (pred.length > 0)
            predictions.add((double) argmax(pred));
        if (backgroundLearner != null)
            backgroundLearner.predict(instance);
        return pred;
    }

    @Override
    public void learnInstance(Instance instance, int totalNumInstances) {
        seenWeight += instance.weight();
        nodeTime++;
        updateStatistics(instance);
        updateObservers(instance);
        updateChangeDetector(instance.classValue());

        if (backgroundLearner != null)
            backgroundLearner.learnInstance(instance, totalNumInstances);

        if (isLeaf() && nodeTime % gracePeriod == 0)
            attemptInitialSplit(instance);

        if (!isLeaf() && nodeTime % minSamplesReevaluate == 0)
            hatGrow();

        if (!isLeaf()) //Do NOT! put this in the upper (!isleaf()) block. This is not the same since we might kill the subtree during reevaluation!
            propagateToSuccessors(instance, totalNumInstances);
    }

    private void hatGrow() {
        if (changeDetector.getChange()) {
            backgroundLearner = newNode(depth, new DoubleVector(), new LinkedList<>(usedNominalAttributes));
            return;
        }
        if (backgroundLearner != null) {
            // we are working with error rates here, not with accuracy.
            if (backgroundLearner.changeDetector.getEstimation() > changeDetector.getEstimation())
                return;

            double e1 = changeDetector.getEstimation();
            double w1 = changeDetector.getWidth();
            double e2 = backgroundLearner.changeDetector.getEstimation();
            double w2 = backgroundLearner.changeDetector.getWidth();
            double significance = switchSignificance(e1, e2, w1, w2);
            if (significance < confidence) {
                makeBackgroundLearnerMainLearner();
            }
        }
    }

    @Override
    protected EFHATNode newNode(int depth, DoubleVector classDistribution, List<Integer> usedNominalAttributes) {
        return new EFHATNode(
                splitCriterion, gracePeriod, confidence, adaptiveConfidence, useAdaptiveConfidence,
                leafPrediction, minSamplesReevaluate, depth, maxDepth,
                tau, binaryOnly, noPrePrune, nominalObserverBlueprint,
                classDistribution, new LinkedList<>(),
                -1,  // we don't block attributes in HT
                (CustomADWINChangeDetector) changeDetector.copy()
        );
    }

    private Double switchSignificance(double e1, double e2, double w1, double w2) {
        if (w1 == 0 || w2 == 0)
            return 1.0;
        double diff = Math.abs(e1 - e2);
        double m = w1 * w2 / (w1 + w2);
        double sig = 2 * Math.exp(-2 * m * Math.pow(diff, 2));
        return sig;
    }

    private void makeBackgroundLearnerMainLearner() {
        splitAttribute = backgroundLearner.splitAttribute;
        splitAttributeIndex = backgroundLearner.splitAttributeIndex;
        successors = backgroundLearner.successors;
        observedClassDistribution = backgroundLearner.observedClassDistribution;
        classDistributionAtTimeOfCreation = backgroundLearner.classDistributionAtTimeOfCreation;
        attributeObservers = backgroundLearner.attributeObservers;
        seenWeight = backgroundLearner.seenWeight;
        nodeTime = backgroundLearner.nodeTime;
        numSplitAttempts = backgroundLearner.numSplitAttempts;
        setSplitTest(backgroundLearner.getSplitTest());
        backgroundLearner = null;
    }

    private void updateChangeDetector(double label) {
        if (predictions.size() == 0)
            return;
        Double pred = predictions.removeFirst();
        if (pred == null)
            return;
        changeDetector.input(pred == label ? 0.0 : 1.0); // monitoring error rate, not accuracy.
    }
}
