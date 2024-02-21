package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.core.DoubleVector;

import java.util.*;

public class Restructurer {
    private final int maxBranchLength;
    private final double acceptedThresholdDeviation;

    public Restructurer(int maxBranchLength,
                        double acceptedNumericThresholdDeviation) {
        this.maxBranchLength = maxBranchLength;
        acceptedThresholdDeviation = acceptedNumericThresholdDeviation;
    }

    public PlasticNode restructure(PlasticNode node, AttributeSplitSuggestion suggestion, Attribute splitAttribute, Double splitValue) {
        boolean isBinary = suggestion.resultingClassDistributions.length == 1;
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

        if (node.splitAttribute.isNumeric()) {
            Double currentSplitValue = node.getSuccessors().getReferenceValue();
            if (node.splitAttribute == splitAttribute && !currentSplitValue.equals(splitValue)) {
                updateThreshold(node, splitAttributeIndex, splitValue);
//                keepSuccessorsResetSplitters(node, splitAttributeIndex, splitValue);
                return node;
            }
        }

        node.collectChildrenSplitAttributes();

        LinkedList<PlasticBranch> mappedTree = mapTree(node, splitAttribute, splitAttributeIndex);
        if (mappedTree == null)
            return null;

        expandMappedTree(mappedTree, splitAttribute, splitAttributeIndex, splitValue);
        LinkedList<PlasticBranch> finalBranches = new LinkedList<>();
        for (PlasticBranch branch: mappedTree) {
            finalBranches.addAll(decoupleLastNode(branch));
        }

        modifyMappedTree(finalBranches, splitAttribute);

        PlasticNode newRoot = reassembleTree(finalBranches);
        // skip step 5 in python
        // skip step 6 in python

//        for (PlasticBranch branch: finalBranches) {
//            System.out.println(branch.getDescription());
//        }

        newRoot.splitAttribute = splitAttribute;
        newRoot.splitAttributeIndex = splitAttributeIndex;
        newRoot.updateUsedNominalAttributesInSubtree(splitAttribute, splitAttributeIndex);
        newRoot.getSuccessors().getAllSuccessors().forEach(s -> cleanupSubtree((PlasticNode) s));

        int i = 0;
        ArrayList<SuccessorIdentifier> sortedKeys = new ArrayList<>(newRoot.getSuccessors().getKeyset());
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
            i++;
        }

        for (CustomEFDTNode s1: newRoot.getSuccessors().getAllSuccessors()) {
            if (s1.isLeaf())
                continue;
            finalPrune((PlasticNode) s1);
        }

        return newRoot;
    }

    private boolean checkPreconditions(PlasticNode node, Attribute splitAttribute, Double splitValue, boolean isBinary) {
        if (node.isLeaf())
            return false;
        if (splitAttribute.isNominal()) {
            if (splitAttribute == node.splitAttribute && !isBinary)
                return false;
        }
        else {
            assert splitValue != null;
            if (node.splitAttribute.isNumeric()) {
                Double currentSplitValue = node.getSuccessors().getReferenceValue();
                if (node.splitAttribute == splitAttribute && currentSplitValue.equals(splitValue))
                    return false;
            }
        }
        return true;
    }

    private LinkedList<PlasticBranch> disconnectRoot(PlasticNode root) {
        Successors successors = root.getSuccessors();
        if (successors == null) {
            return null;
        }
        if (successors.size() == 0) {
            return null;
        }
        Set<SuccessorIdentifier> keys = successors.getKeyset();
        LinkedList<PlasticBranch> branches = new LinkedList<>();
        for (SuccessorIdentifier key: keys) {
            PlasticTreeElement newElement = new PlasticTreeElement(root, key);
            PlasticBranch newBranch = new PlasticBranch();
            newBranch.getBranchRef().add(newElement);
            branches.add(newBranch);
        }
        return branches;
    }

    private PlasticBranch disconnectSuccessors(PlasticTreeElement ancestor, Attribute swapAttribute) {
        PlasticBranch branchExtensions = new PlasticBranch();
        PlasticNode ancestorNode = ancestor.getNode();
        SuccessorIdentifier key = ancestor.getKey();
        PlasticNode successor = (PlasticNode) ancestorNode.getSuccessors().getSuccessorNode(key);

        boolean endCondition = successor.isLeaf() || successor.getSplitAttribute() == swapAttribute;

        if (endCondition) {
            PlasticTreeElement element = new PlasticTreeElement(successor, null);
            branchExtensions.getBranchRef().add(element);
        }
        else {
            for (SuccessorIdentifier successorSuccessorKey: successor.getSuccessors().getKeyset()) {
                PlasticTreeElement element = new PlasticTreeElement(successor, successorSuccessorKey);
                branchExtensions.getBranchRef().add(element);
            }
        }
        return branchExtensions;
    }

    private LinkedList<PlasticBranch> mapBranches(
        LinkedList<PlasticBranch> branches,
        Attribute swapAttribute,
        int swapAttributeIndex,
        int maxBranchLength
    ) {
        LinkedList<PlasticBranch> newBranches = new LinkedList<>();

        boolean allFinished = true;
        for (PlasticBranch branch: branches) {
            allFinished &= getEndConditionForBranch(branch, swapAttribute, maxBranchLength);
        }
        if (allFinished)
            return branches;

        for (PlasticBranch branch: branches) {
            PlasticBranch linkedBranch = (PlasticBranch) branch;
            boolean branchIsFinished = getEndConditionForBranch(branch, swapAttribute, maxBranchLength);
            if (branchIsFinished) {
                newBranches.add(branch); // keep the branch as it is
                continue;
            }
            PlasticTreeElement lastElement = branch.getLast();
            PlasticBranch branchExtensions = disconnectSuccessors(lastElement, swapAttribute);
            for (PlasticTreeElement extension: branchExtensions.getBranchRef()) {
                PlasticBranch extendedBranch = new PlasticBranch((LinkedList<PlasticTreeElement>) linkedBranch.getBranchRef().clone());
                extendedBranch.getBranchRef().add(extension);
                newBranches.add(extendedBranch);
            }
        }
        return mapBranches(newBranches, swapAttribute, swapAttributeIndex, maxBranchLength);
    }

    private boolean getEndConditionForBranch(PlasticBranch branch, Attribute swapAttribute, int maxBranchLength) {
        CustomEFDTNode lastNodeOfBranch = branch.getLast().getNode();
        boolean isSwapAttribute = lastNodeOfBranch.getSplitAttribute() == swapAttribute;
        boolean isLeaf = lastNodeOfBranch.isLeaf();
        if (isSwapAttribute || isLeaf)
            return true;
        boolean swapAttributeInChildren = false;
        for (CustomEFDTNode successor: lastNodeOfBranch.getSuccessors().getAllSuccessors()) {
            Set<Attribute> splitAttributesOfChildren = ((PlasticNode) successor).getChildrenSplitAttributes();
            if (splitAttributesOfChildren.contains(swapAttribute)) {
                swapAttributeInChildren = true;
                break;
            }
        }
        if (swapAttributeInChildren)
            return false;
        return branch.getBranchRef().size() == maxBranchLength;
    }

    private LinkedList<PlasticBranch> mapTree(PlasticNode root, Attribute swapAttribute, int swapAttributeIndex) {
        LinkedList<PlasticBranch> initialBranches = disconnectRoot(root);
        if (initialBranches == null)
            return null;
        return mapBranches(initialBranches, swapAttribute, swapAttributeIndex, maxBranchLength);
    }

    private void modifyMappedTree(LinkedList<PlasticBranch> mappedTree, Attribute swapAttribute) {
        mappedTree.forEach(branch -> modifyBranch(branch, swapAttribute));
    }

    private void splitLastNodeIfRequiredCategorical(PlasticBranch branch,
                                    Attribute swapAttribute,
                                    int swapAttributeIndex,
                                    Double splitValue) {
        PlasticNode lastNode = branch.getLast().getNode();
        boolean forceSplit = lastNode.isLeaf();
        forceSplit |= lastNode.getSplitAttribute() != swapAttribute;

        if (
                !lastNode.isLeaf()
                && lastNode.getSplitAttribute().isNominal()
                && lastNode.getSuccessors().isBinary()
                && splitValue != null
        ) {
            Double currentSplitValue = lastNode.getSuccessors().getReferenceValue();
            boolean rightAttributeWrongValue = lastNode.getSplitAttribute() == swapAttribute && !Objects.equals(currentSplitValue, splitValue);
            if (rightAttributeWrongValue) {
                //TODO: we might be able to just keep everything if we have a binary nominal split
                Successors lastNodeSuccessors = lastNode.getSuccessors();
                Attribute lastNodeSplitAttribute = lastNode.getSplitAttribute();
                int lastNodeSplitAttributeIndex = lastNode.getSplitAttributeIndex();
                InstanceConditionalTest splitTest = lastNode.getSplitTest();
                lastNode.forceSplit(
                        swapAttribute,
                        swapAttributeIndex,
                        splitValue,
                        true
                );
                PlasticNode defaultSuccessor = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(SuccessorIdentifier.DEFAULT_NOMINAL_VALUE);
                defaultSuccessor.transferSplit(
                        lastNodeSuccessors, lastNodeSplitAttribute, lastNodeSplitAttributeIndex, splitTest
                );
                return;
            }
        }
        if (forceSplit) {
            lastNode.forceSplit(
                    swapAttribute,
                    swapAttributeIndex,
                    splitValue,
                    splitValue != null
            );
            if (lastNode.isLeaf()) {
                System.out.println("Error");
            }
            lastNode.successors.getAllSuccessors().forEach(s -> ((PlasticNode) s).setIsArtificial());
        }
        else if (lastNode.isLeaf()) {
            System.out.println("Do nothing");
        }
    }

    private void splitLastNodeIfRequiredNumeric(PlasticBranch branch,
                                                Attribute swapAttribute,
                                                int swapAttributeIndex,
                                                Double splitValue) {
        assert splitValue != null;
        PlasticNode lastNode = branch.getLast().getNode();
        SuccessorIdentifier lastSuccessorId = branch.getLast().getKey();
        boolean forceSplit = lastNode.isLeaf();
        forceSplit |= lastNode.splitAttribute != swapAttribute;

        if (forceSplit) {
            lastNode.forceSplit(
                    swapAttribute,
                    swapAttributeIndex,
                    splitValue,
                    true
            );
            lastNode.getSuccessors().getAllSuccessors().forEach(s -> ((PlasticNode) s).setIsArtificial());
            return;
        }

        Double oldThreshold = lastNode.getSuccessors().getReferenceValue();
        if (splitValue.equals(oldThreshold))
            return; // do nothing

//        if (Math.abs(splitValue - oldThreshold) < acceptedThresholdDeviation) {
//            SuccessorIdentifier leftKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, true);
//            SuccessorIdentifier rightKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, false);
//            PlasticNode succ1 = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(leftKey);
//            PlasticNode succ2 = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(rightKey);
//            Successors newSuccessors = new Successors(true, true, splitValue);
//            newSuccessors.addSuccessorNumeric(splitValue, succ1, true);
//            newSuccessors.addSuccessorNumeric(splitValue, succ2, false);
//            lastNode.successors = newSuccessors;
//            return;
//        }

        if (lastNode.getSuccessors().size() > 1) {
            updateThreshold(lastNode, swapAttributeIndex, splitValue);
//            keepSuccessorsResetSplitters(lastNode, swapAttributeIndex, splitValue);
            return;
        }

        lastNode.forceSplit(
                swapAttribute,
                swapAttributeIndex,
                splitValue,
                true
        );
        lastNode.getSuccessors().getAllSuccessors().forEach(s -> ((PlasticNode) s).setIsArtificial());
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
            if (Math.abs(splitValue - oldThreshold) <= acceptedThresholdDeviation) {
                setRestructuredFlagInSubtree(s);
            }
        }
    }

    private void keepSuccessorsResetSplitters(PlasticNode lastNode, int swapAttributeIndex, double splitValue) {
        PlasticNode R = lastNode;
        R.setRestructuredFlag();
        Attribute splitAttribute = R.getSplitAttribute();
        Integer splitAttributeIndex = R.getSplitAttributeIndex();

        Set<SuccessorIdentifier> oldKeys = lastNode.getSuccessors().getKeyset();
        Double oldSplitValue = lastNode.getSuccessors().getReferenceValue();
        Successors oldSuccessors = lastNode.getSuccessors();
        InstanceConditionalTest oldTest = lastNode.getSplitTest();

        PlasticNode A = null;
        PlasticNode B = null;

        if (lastNode.getSuccessors().size() == 2) {
            SuccessorIdentifier keyA = new SuccessorIdentifier(true, oldSplitValue, oldSplitValue, true);
            SuccessorIdentifier keyB = new SuccessorIdentifier(true, oldSplitValue, oldSplitValue, false);
            A = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(keyA);
            B = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(keyB);
        }
        else if (lastNode.getSuccessors().size() == 1) {
            SuccessorIdentifier onlyExistingKeyInSuccessors = lastNode.getSuccessors().getKeyset().iterator().next();
            boolean isLower = onlyExistingKeyInSuccessors.isLower();
            if (isLower) {
                A = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(onlyExistingKeyInSuccessors);
                B = lastNode.newNode(
                        lastNode.depth + 1,
                        new DoubleVector(),
                        lastNode.getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex)
                );
            }
            else {
                A = lastNode.newNode(
                        lastNode.depth + 1,
                        new DoubleVector(),
                        lastNode.getUsedNominalAttributesForSuccessor(splitAttribute, splitAttributeIndex)
                );
                B = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(onlyExistingKeyInSuccessors);
            }
            oldKeys.add(
                    new SuccessorIdentifier(true, oldSplitValue, oldSplitValue, !isLower)
            );
        }

        boolean expandLeft = oldSplitValue < splitValue;
        PlasticNode N1 = expandLeft ? A : B;
        PlasticNode N2 = expandLeft ? B : A;

        resetObserversInNode(N2);
        R.forceSplit(
                splitAttribute,
                swapAttributeIndex,
                splitValue,
                true
        );

        R.successors.forceSuccessorForKey(
                new SuccessorIdentifier(true, splitValue, splitValue, !expandLeft),
                N2
        );
        PlasticNode otherRootSuccessor = (PlasticNode) R.getSuccessors().getSuccessorNode(
                new SuccessorIdentifier(true, splitValue, splitValue, expandLeft)
        );

        otherRootSuccessor.transferSplit(
                oldSuccessors,
                splitAttribute,
                splitAttributeIndex,
                oldTest
        );
        SuccessorIdentifier keyInBetween = new SuccessorIdentifier(
                true, oldSplitValue, oldSplitValue, true //TODO: Doublecheck
        );

        PlasticNode nodeInBetween = N2.newNode(
                N2.getDepth() + 1,
                new DoubleVector(N2.getObservedClassDistribution().clone()),
                new LinkedList<>(N2.getUsedNominalAttributes())
                );
        N1.incrementDepthInSubtree();

        PlasticNode newLeafSuccessor = nodeInBetween.newNode(
                nodeInBetween.getDepth(),
                new DoubleVector(nodeInBetween.getObservedClassDistribution()),
                new LinkedList<>(nodeInBetween.getUsedNominalAttributes())
                );

        otherRootSuccessor.getSuccessors().forceSuccessorForKey(
                keyInBetween, newLeafSuccessor
        );
    }

    private void resetObserversInNode(PlasticNode node) {
        node.resetObservers();
        if (node.isLeaf())
            return;
        for (CustomEFDTNode successor: node.getSuccessors().getAllSuccessors()) {
            resetObserversInNode((PlasticNode) successor);
        }
    }

    private void expandMappedTree(LinkedList<PlasticBranch> mappedTree, Attribute splitAttribute, int splitAttributeIndex, Double splitValue) {
        for (PlasticBranch branch: mappedTree) {
            if (splitAttribute.isNominal())
                splitLastNodeIfRequiredCategorical(branch, splitAttribute, splitAttributeIndex, splitValue);
            else
                splitLastNodeIfRequiredNumeric(branch, splitAttribute, splitAttributeIndex, splitValue);
        }
    }

    private LinkedList<PlasticBranch> decoupleLastNode(PlasticBranch branch) {
        PlasticNode lastNode = branch.getLast().getNode();
        LinkedList<PlasticBranch> decoupledBranches = new LinkedList<>();
        Successors lastNodeSuccessors = lastNode.getSuccessors();

        for (SuccessorIdentifier key: lastNodeSuccessors.getKeyset()) {
            PlasticBranch branchCopy = branch.copy();  //TODO: Doublecheck if this is fine.
            PlasticTreeElement replacedEndElement = new PlasticTreeElement(branchCopy.getLast().getNode(), key);
            branchCopy.getBranchRef().removeLast();
            branchCopy.getBranchRef().add(replacedEndElement);
            PlasticTreeElement finalElement = new PlasticTreeElement((PlasticNode) lastNodeSuccessors.getSuccessorNode(key), null);
            branchCopy.getBranchRef().add(finalElement);
            decoupledBranches.add(branchCopy);
        }

        return decoupledBranches;
    }

    private void setDepth(PlasticBranch branch) {
        PlasticNode firstNode = branch.getBranchRef().getFirst().getNode();
        int i = 0;
        for (PlasticTreeElement item: branch.getBranchRef()) {
            item.getNode().depth = firstNode.getDepth() + i;
            i++;
        }
    }

    private void setRestructuredFlagInBranch(PlasticBranch branch) {
        if (branch.getBranchRef().size() <= 2)
            return;
        int i = 0;
        for (PlasticTreeElement item: branch.getBranchRef()) {
            if (i == 0 || i == branch.getBranchRef().size() - 1) {
                i++;
                continue;
            }
            item.getNode().setRestructuredFlag();
            i++;
        }
    }

    private void resetSuccessorsInBranch(PlasticBranch branch) {
        if (branch.getBranchRef().size() == 1)
            return;
        int i = 0;
        for (PlasticTreeElement item: branch.getBranchRef()) {
            if (i == branch.getBranchRef().size() - 1)
                break;
            item.getNode().successors = new Successors(item.getNode().getSuccessors(), false);
            i++;
        }
    }

    private void putLastElementToFront(PlasticBranch branch, Attribute splitAttribute) {
        PlasticTreeElement oldFirstBranchElement = branch.getBranchRef().getFirst();
        PlasticTreeElement newFirstBranchElement = branch.getBranchRef().remove(branch.getBranchRef().size() - 2);
        branch.getBranchRef().addFirst(newFirstBranchElement);

        PlasticNode oldFirstNode = oldFirstBranchElement.getNode();
        PlasticNode newFirstNode = newFirstBranchElement.getNode();

        //TODO not sure this is actually required! I think it could be sufficient to just change the successors when building the tree in a later step.
        newFirstNode.observedClassDistribution = oldFirstNode.observedClassDistribution;
        newFirstNode.depth = oldFirstNode.depth;
        newFirstNode.attributeObservers = oldFirstNode.attributeObservers;
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

    private void modifyBranch(PlasticBranch branch, Attribute splitAttribute) {
        putLastElementToFront(branch, splitAttribute);
        resetSuccessorsInBranch(branch);
        setRestructuredFlagInBranch(branch);
        setDepth(branch);
    }

    private void cleanupSubtree(PlasticNode node) {
        if (node.getRestructuredFlag()) {
            if (!node.isLeaf()) {
                node.observedClassDistribution = new DoubleVector();
                node.classDistributionAtTimeOfCreation = new DoubleVector();
            }
            node.resetInfogainTracking();
            node.resetObservers();
            node.seenWeight = 0.0;
            node.nodeTime = 0;
            node.numSplitAttempts = 0;
            node.resetRestructuredFlag();
        }
        if (!node.isLeaf())
            node.successors.getAllSuccessors().forEach(s -> cleanupSubtree((PlasticNode) s));
    }

    private void finalPrune(PlasticNode node) {
        if (node.isLeaf())
            return;
        Set<SuccessorIdentifier> keys = new HashSet<>(node.getSuccessors().getKeyset());

        for (SuccessorIdentifier key: node.getSuccessors().getKeyset()) {
            PlasticNode successor = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            collectStats(successor);
        }

        for (SuccessorIdentifier key: keys) {
            PlasticNode thisNode = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            if (thisNode.isDummy() || thisNode.isArtificial()) {
                node.getSuccessors().removeSuccessor(key);
            }
        }

        if (node.getSuccessors().size() == 0) {
            node.killSubtree();
            node.resetSplitAttribute();
            return;
        }
        for (SuccessorIdentifier key: node.getSuccessors().getKeyset()) {
            PlasticNode successor = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            finalPrune(successor);
        }
    }

    private DoubleVector collectStats(CustomEFDTNode node) {
        if (node.isLeaf()) {
            return node.observedClassDistribution;
        }
        else {
            DoubleVector stats = new DoubleVector();
            for (CustomEFDTNode successor : node.getSuccessors().getAllSuccessors()) {
                DoubleVector fromSuccessor = collectStats(successor);
                stats.addValues(fromSuccessor);
            }
            node.observedClassDistribution = stats;
            return stats;
        }
    }

    private void removeUnreachableSubtree(PlasticNode node, int splitAttributeIndex, double threshold, boolean isLower) {
        if (node.isLeaf())
            return;

        if (node.splitAttributeIndex != splitAttributeIndex) {
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
}
