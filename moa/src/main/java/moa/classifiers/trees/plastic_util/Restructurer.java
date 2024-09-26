package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import moa.AbstractMOAObject;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.core.DoubleVector;

import java.util.*;

public class Restructurer extends AbstractMOAObject {
    private final int maxBranchLength;
    private final double acceptedThresholdDeviation;

    public Restructurer(int maxBranchLength,
                        double acceptedNumericThresholdDeviation) {
        this.maxBranchLength = maxBranchLength;
        acceptedThresholdDeviation = acceptedNumericThresholdDeviation;
    }

    public PlasticNode restructure(PlasticNode node, AttributeSplitSuggestion suggestion, Attribute splitAttribute, Double splitValue) {
        boolean isBinary = !(suggestion.splitTest instanceof NominalAttributeMultiwayTest);
        int splitAttributeIndex = suggestion.splitTest.getAttsTestDependsOn()[0];

        boolean checkSucceeds = checkPreconditions(node, splitAttribute, splitValue, isBinary);

        if (!checkSucceeds)
            return null;

        if (splitAttribute == node.splitAttribute && isBinary) {
            assert splitValue != null;
            Double currentNominalBinarysplitValue = node.getSuccessors().getReferenceValue();
            if (currentNominalBinarysplitValue.equals(splitValue))
                return node;
        }

        if (node.splitAttribute.isNumeric() && splitAttribute.isNumeric()) {
            assert splitValue != null;
            Double currentSplitValue = node.getSuccessors().getReferenceValue();
            if (node.splitAttribute == splitAttribute) {
                if (!currentSplitValue.equals(splitValue))
                    updateThreshold(node, splitAttributeIndex, splitValue);
                return node;
            }
        }

        node.collectChildrenSplitAttributes();
        MappedTree mappedTree = new MappedTree(node, splitAttribute, splitAttributeIndex, splitValue, maxBranchLength);
        PlasticNode newRoot = reassembleTree(mappedTree);

        newRoot.setSplitAttribute(suggestion, splitAttribute);
        newRoot.updateUsedNominalAttributesInSubtree(splitAttribute, splitAttributeIndex);

        // Reset counters in restructured nodes
        newRoot.getSuccessors().getAllSuccessors().forEach(s -> cleanupSubtree((PlasticNode) s));

        // Initialize the statistics of the root's direct successors
        List<SuccessorIdentifier> sortedKeys = new LinkedList<>(newRoot.getSuccessors().getKeyset());
        Collections.sort(sortedKeys);
        for (SuccessorIdentifier key: sortedKeys) {
            CustomEFDTNode successor = newRoot.getSuccessors().getSuccessorNode(key);
            if (splitAttribute.isNominal()) {
                double selectorValue = key.getSelectorValue();
                if (selectorValue == SuccessorIdentifier.DEFAULT_NOMINAL_VALUE) {
                    assert isBinary;
                    successor.observedClassDistribution = new DoubleVector(suggestion.resultingClassDistributions[1]);
                    successor.classDistributionAtTimeOfCreation = new DoubleVector(suggestion.resultingClassDistributions[1]);
                }
                else if (selectorValue < suggestion.numSplits()) {
                    successor.observedClassDistribution = new DoubleVector(suggestion.resultingClassDistributionFromSplit((int) selectorValue));
                    successor.classDistributionAtTimeOfCreation = new DoubleVector(suggestion.resultingClassDistributionFromSplit((int) selectorValue));
                }
                else {
                    successor.observedClassDistribution = new DoubleVector();
                    successor.classDistributionAtTimeOfCreation = new DoubleVector();
                }
            }
            else {
                if (key.isLower()) {
                    successor.observedClassDistribution = new DoubleVector(suggestion.resultingClassDistributions[0]);
                    successor.classDistributionAtTimeOfCreation = new DoubleVector(suggestion.resultingClassDistributions[0]);
                }
                else {
                    successor.observedClassDistribution = new DoubleVector(suggestion.resultingClassDistributions[1]);
                    successor.classDistributionAtTimeOfCreation = new DoubleVector(suggestion.resultingClassDistributions[1]);
                }
            }
        }

        // Prune the artificial leaves
        finalPrune(node);
        return newRoot;
    }

    private boolean checkPreconditions(PlasticNode node, Attribute splitAttribute, Double splitValue, boolean isBinary) {
        if (node.isLeaf())
            return false;
        if (splitAttribute.isNominal()) {
            if (node.getSplitTest() instanceof NominalAttributeBinaryTest && isBinary) {
                if (
                        ((NominalAttributeBinaryTest) node.getSplitTest()).getValue() == splitValue
                                && splitAttribute == node.splitAttribute
                ) {
                    System.err.println("This should never be triggered. A binary re-split with the same attribute and split value should never happen");
                }
            }
            else if (node.getSplitTest() instanceof NominalAttributeMultiwayTest) {
                if (splitAttribute == node.splitAttribute && !isBinary)
                    System.err.println("This should never be triggered. A multiway re-split on the same nominal attribute should never happen");
            }
        }
        return true;
    }

    private void updateThreshold(PlasticNode node, int splitAttributeIndex, double splitValue) {
        Double oldThreshold = node.getSuccessors().getReferenceValue();

        SuccessorIdentifier leftKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, true);
        SuccessorIdentifier rightKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, false);
        PlasticNode succ1 = (PlasticNode) node.getSuccessors().getSuccessorNode(leftKey);
        PlasticNode succ2 = (PlasticNode) node.getSuccessors().getSuccessorNode(rightKey);
        Successors newSuccessors = new Successors(true, true, splitValue);
        if (succ1 != null)
            newSuccessors.addSuccessorNumeric(splitValue, succ1, true);
        if (succ2 != null)
            newSuccessors.addSuccessorNumeric(splitValue, succ2, false);
        node.successors = newSuccessors;

        if (node.isLeaf())
            return;

        for (SuccessorIdentifier key: node.getSuccessors().getKeyset()) {
            PlasticNode s = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            removeUnreachableSubtree(s, splitAttributeIndex, splitValue, key.isLower());
        }

        if (Math.abs(splitValue - oldThreshold) > acceptedThresholdDeviation) {
            setRestructuredFlagInSubtree(node);
        }
    }

    private PlasticNode reassembleTree(LinkedList<PlasticBranch> mappedTree) {
        if (mappedTree.size() == 0) {
            System.out.println("MappedTree is empty");
        }

        PlasticNode root = mappedTree.getFirst().getBranchRef().getFirst().getNode();
        for (PlasticBranch branch: mappedTree) {
            PlasticNode currentNode = root;

            int depth = 0;
            for (PlasticTreeElement thisElement: branch.getBranchRef()) {
                if (depth == branch.getBranchRef().size() - 1)
                    break;

                PlasticNode thisNode = thisElement.getNode();
                SuccessorIdentifier thisKey = thisElement.getKey();
                if (currentNode.getSplitAttribute() == thisNode.getSplitAttribute()) {
                    if (currentNode.getSuccessors().contains(thisKey)) {
                        currentNode = (PlasticNode) currentNode.getSuccessors().getSuccessorNode(thisKey);
                    }
                    else {
                        PlasticNode newSuccessor = branch.getBranchRef().get(depth + 1).getNode();
                        boolean success = currentNode.getSuccessors().addSuccessor(newSuccessor, thisKey);
                        assert success;
                        currentNode = newSuccessor;
                    }
                }
                depth++;
            }
        }
        return root;
    }

    private PlasticNode reassembleTree(MappedTree mappedTree) {
        if (!mappedTree.hasNext()) {
            System.out.println("MappedTree is empty");
        }

        PlasticNode root = null;
        while (mappedTree.hasNext()) {
            PlasticBranch branch = mappedTree.next();
            if (root == null)
                root = branch.getBranchRef().getFirst().getNode();

            PlasticNode currentNode = root;

            int depth = 0;
            for (PlasticTreeElement thisElement: branch.getBranchRef()) {
                if (depth == branch.getBranchRef().size() - 1)
                    break;

                PlasticNode thisNode = thisElement.getNode();
                SuccessorIdentifier thisKey = thisElement.getKey();
                if (currentNode.getSplitAttribute() == thisNode.getSplitAttribute()) {
                    if (currentNode.getSuccessors().contains(thisKey)) {
                        currentNode = (PlasticNode) currentNode.getSuccessors().getSuccessorNode(thisKey);
                    }
                    else {
                        PlasticNode newSuccessor = branch.getBranchRef().get(depth + 1).getNode();
                        boolean success = currentNode.getSuccessors().addSuccessor(newSuccessor, thisKey);
                        assert success;
                        currentNode = newSuccessor;
                    }
                }
                depth++;
            }
        }
        return root;
    }

    private void cleanupSubtree(PlasticNode node) {
        if (!node.getRestructuredFlag())
            return;
        if (!node.isLeaf()) {
            node.observedClassDistribution = new DoubleVector();
            node.classDistributionAtTimeOfCreation = new DoubleVector();
        }
        node.resetObservers();
        node.seenWeight = 0.0;
        node.nodeTime = 0;
        node.numSplitAttempts = 0;
        if (!node.isLeaf())
            node.successors.getAllSuccessors().forEach(s -> cleanupSubtree((PlasticNode) s));
    }

    private void finalPrune(PlasticNode node) {
        node.setIsArtificial(false);
        if (node.isLeaf()) {
            return;
        }

        Set<SuccessorIdentifier> keys = new HashSet<>(node.getSuccessors().getKeyset());
        boolean allSuccessorsArePure = true;
        for (SuccessorIdentifier key : keys) {
            PlasticNode thisNode = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            if (thisNode.isDummy()) {
                node.getSuccessors().removeSuccessor(key);
            }
//            if (thisNode.isArtificial()) {
//                node.getSuccessors().removeSuccessor(key);
//            }
            if (!thisNode.isPure())
                allSuccessorsArePure = false;
        }

        if (node.isLeaf() || node.depth >= node.maxDepth) {
            node.observedClassDistribution = collectStatsFromSuccessors(node);
            node.killSubtree();
            node.resetSplitAttribute();
            return;
        }

        if ((allSuccessorsArePure && node.getMajorityVotesOfLeaves().size() <= 1)) {
            node.observedClassDistribution = collectStatsFromSuccessors(node);
            node.killSubtree();
            node.resetSplitAttribute();
            return;
        }

        for (SuccessorIdentifier key : node.getSuccessors().getKeyset()) {
            PlasticNode successor = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            finalPrune(successor);
        }
    }

    private DoubleVector collectStatsFromSuccessors(CustomEFDTNode node) {
        if (node.isLeaf()) {
            return node.observedClassDistribution;
        }
        else {
            DoubleVector stats = new DoubleVector();
            for (CustomEFDTNode successor : node.getSuccessors().getAllSuccessors()) {
                DoubleVector fromSuccessor = successor.observedClassDistribution; //collectStatsFromSuccessors(successor);
                stats.addValues(fromSuccessor);
            }
            return stats;
        }
    }

    private void removeUnreachableSubtree(PlasticNode node, int splitAttributeIndex, double threshold, boolean isLower) {
        if (node.isLeaf())
            return;

        if (node.getSplitAttributeIndex() != splitAttributeIndex) {
            for (CustomEFDTNode successor: node.getSuccessors().getAllSuccessors()) {
                removeUnreachableSubtree((PlasticNode) successor, splitAttributeIndex, threshold, isLower);
            }
            return;
        }

        Set<SuccessorIdentifier> keysToRemove = new HashSet<>();
        for (SuccessorIdentifier key: node.getSuccessors().getKeyset()) {
            assert key.isNumeric();
            if (isLower) {
                if (!key.isLower() && key.getSelectorValue() >= threshold) {
                    keysToRemove.add(key);
                }
            }
            else {
                if (key.isLower() && key.getSelectorValue() <= threshold) {
                    keysToRemove.add(key);
                }
            }
        }
        for (SuccessorIdentifier key: keysToRemove) {
            node.getSuccessors().removeSuccessor(key);
        }

        if (!node.isLeaf()) {
            node.getSuccessors().getAllSuccessors().forEach(s -> removeUnreachableSubtree((PlasticNode) s, splitAttributeIndex, threshold, isLower));
        }
    }

    private void setRestructuredFlagInSubtree(PlasticNode node) {
        if (node.isLeaf())
            return;
        node.setRestructuredFlag();
        node.getSuccessors().getAllSuccessors().forEach(s -> setRestructuredFlagInSubtree((PlasticNode) s));
    }

    @Override
    public void getDescription(StringBuilder sb, int indent) {}
}
