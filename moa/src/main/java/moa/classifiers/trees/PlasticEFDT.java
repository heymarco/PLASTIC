package moa.classifiers.trees;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import moa.classifiers.core.attributeclassobservers.NominalAttributeClassObserver;
import moa.classifiers.core.splitcriteria.SplitCriterion;
import moa.classifiers.trees.plastic_util.PlasticNode;
import moa.core.DoubleVector;

import java.util.ArrayList;

public class PlasticEFDT extends LightweightEFDT {

    public PlasticEFDT() {
        super();
        relMinDeltaG = new FloatOption("relMinDeltaG",
                'G', "Relative minimum information gain to split a tie during reevaluation.",
                0.0, 0.0, 1.0);
    }

    public IntOption maxBranchLengthOption = new IntOption(
            "maxBranchLength",
            'B',
            "Maximum allowed length of branches during restructuring.",
            5, 1, Integer.MAX_VALUE);

    public FloatOption acceptedNumericThresholdDeviationOption = new FloatOption("acceptedNumericThresholdDeviation",
            'Z', "The accepted deviation between the current numeric threshold of a node and the desired threshold. If the absolute difference is smaller, we keep the successors and simply adjust the threshold.",
            0.05, 0.0, 1_000_000.0);

    @Override
    PlasticNode createRoot() {
        return new PlasticNode(
                (SplitCriterion) getPreparedClassOption(splitCriterionOption),
                gracePeriodOption.getValue(),
                splitConfidenceOption.getValue(),
                adaptiveConfidenceOption.getValue(),
                useAdaptiveConfidenceOption.isSet(),
                disableBlockParentSplitAttribute.isSet(),
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
                maxBranchLengthOption.getValue(),
                acceptedNumericThresholdDeviationOption.getValue(),
                null
        );
    }
}
