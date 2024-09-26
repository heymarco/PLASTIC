package moa.classifiers.trees;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.capabilities.Capabilities;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.core.attributeclassobservers.DiscreteAttributeClassObserver;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.plastic_util.CustomEFDTNode;
import moa.classifiers.trees.plastic_util.MeasuresNumberOfLeaves;
import moa.classifiers.trees.plastic_util.PerformsTreeRevision;
import moa.core.DoubleVector;
import moa.core.Measurement;
import moa.options.ClassOption;

import java.util.ArrayList;

public class CustomEFDT extends AbstractClassifier implements MultiClassClassifier, PerformsTreeRevision, MeasuresNumberOfLeaves {

    private static final long serialVersionUID = 3L;

    @Override
    public String getPurposeString() {
        return "Lightweight Implementation of EFDT";
    }


    CustomEFDTNode root;
    int seenItems = 0;

    public IntOption gracePeriodOption = new IntOption(
            "gracePeriod",
            'g',
            "The number of instances a leaf should observe between split attempts.",
            200, 0, Integer.MAX_VALUE);

    public IntOption reEvalPeriodOption = new IntOption(
            "reevaluationPeriod",
            'R',
            "The number of instances an internal node should observe between re-evaluation attempts.",
            200, 0, Integer.MAX_VALUE);

    public ClassOption nominalEstimatorOption = new ClassOption("nominalEstimator",
            'd', "Nominal estimator to use.", DiscreteAttributeClassObserver.class,
            "NominalAttributeClassObserver");

    public ClassOption splitCriterionOption = new ClassOption("splitCriterion",
            's', "Split criterion to use.", SplitCriterion.class,
            "InfoGainSplitCriterion");

    public FloatOption splitConfidenceOption = new FloatOption(
            "splitConfidence",
            'c',
            "The allowable error in split decision when using fixed confidence. Values closer to 0 will take longer to decide.",
            0.001, 0.0, 1.0);

    public FloatOption adaptiveConfidenceOption = new FloatOption(
            "adaptiveSplitConfidence",
            'C',
            "The initial allowable error in split decision when using adaptive confidence. Values closer to 0 will take longer to decide.",
            0.2, 0.0, 1.0);

    public FlagOption useAdaptiveConfidenceOption = new FlagOption(
            "useAdaptiveConfidence",
            'a',
            "Flag if confidence should be adaptive (decreasing over time).");

    public FloatOption tieThresholdOption = new FloatOption("tieThreshold",
            't', "Threshold below which a split will be forced to break ties.",
            0.05, 0.0, 1.0);

    public FloatOption tieThresholdReevalOption = new FloatOption("tieThresholdReevaluation",
            'T', "Threshold below which a split will be forced to break ties during reevaluation.",
            0.05, 0.0, 1.0);

    public FloatOption relMinDeltaG = new FloatOption("relMinDeltaG",
            'G', "Relative minimum information gain to split a tie during reevaluation.",
            0.5, 0.0, 1.0);

    public FlagOption binarySplitsOption = new FlagOption("binarySplits", 'b',
            "Only allow binary splits.");

    public MultiChoiceOption leafpredictionOption = new MultiChoiceOption(
            "leafprediction", 'l', "Leaf prediction to use.", new String[]{
            "MC", "NB"}, new String[]{
            "Majority class", "Naive Bayes"}, 0);

    public IntOption maxDepthOption = new IntOption(
            "maxDepth",
            'D',
            "Maximum allowed depth of tree.",
            20, 0, Integer.MAX_VALUE);

    public FlagOption noPrePruneOption = new FlagOption("noPrePrune", 'p',
            "Disable pre-pruning.");

    /**
     * Creates and configures the root node of the tree
     * <p>
     * The root is the only access point for the main PLASTIC class. All subtrees etc will simply connect to the root.
     * <p>
     * @return the created root node
     **/
    CustomEFDTNode createRoot() {
        return new CustomEFDTNode(
                (SplitCriterion) getPreparedClassOption(splitCriterionOption),
                gracePeriodOption.getValue(),
                splitConfidenceOption.getValue(),
                adaptiveConfidenceOption.getValue(),
                useAdaptiveConfidenceOption.isSet(),
                leafpredictionOption.getChosenLabel(),
                reEvalPeriodOption.getValue(),
                0,
                maxDepthOption.getValue(),
                tieThresholdOption.getValue(),
                tieThresholdReevalOption.getValue(),
                relMinDeltaG.getValue(),
                binarySplitsOption.isSet(),
                noPrePruneOption.isSet(),
                (NominalAttributeClassObserver) getPreparedClassOption(nominalEstimatorOption),
                new DoubleVector(),
                new ArrayList<>(),
                -1
        );
    }

    @Override
    public Capabilities getCapabilities() {
        return super.getCapabilities();
    }

    @Override
    public double[] getVotesForInstance(Instance inst) {
        if (root == null) {
            root = createRoot();
            return new double[inst.numClasses()];
        }
        return root.predict(inst);
    }

    @Override
    public void resetLearningImpl() {
        root = null;
        seenItems = 0;
    }

    /**
     * Trains the tree on the provided instance
     * @param inst The instance to train on
     **/
    @Override
    public void trainOnInstanceImpl(Instance inst) {
        if (root == null)
            root = createRoot();
        root.learnInstance(inst, seenItems);
        seenItems++;
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return new Measurement[0];
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {}

    @Override
    public boolean isRandomizable() {
        return false;
    }

    /**
     * Checks if a tree revision (e.g., pruning or restructuring) was performed at the current time step
     *
     * @return if a tree revision was performed
     **/
    @Override
    public boolean didPerformTreeRevision() {
        return root.didPerformTreeRevision();
    }

    /**
     * Returns the number of leaves of the tree.
     *
     * <p>
     *     Note that calling this function repeatedly causes some overhead
     *     as the function traverses the tree recursively.
     * </p>
     *
     * @return the created root node
     **/
    @Override
    public int getLeafNumber() {
        return root.getLeafNumber();
    }
}
