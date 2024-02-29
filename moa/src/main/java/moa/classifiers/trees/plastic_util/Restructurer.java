package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import moa.classifiers.core.AttributeSplitSuggestion;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
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

//        for (CustomEFDTNode s1: node.getSuccessors().getAllSuccessors()) {
//            if (s1.isLeaf())
//                continue;
//            initialPrune((PlasticNode) s1);
//        }

        node.collectChildrenSplitAttributes();

        MappedTree mappedTree = new MappedTree(node, splitAttribute, splitAttributeIndex, splitValue, maxBranchLength);

//        LinkedList<PlasticBranch> mappedTree = mapTree(node, splitAttribute, splitValue, splitAttributeIndex);
//        if (mappedTree == null)
//            return null;

//        expandMappedTree(mappedTree, splitAttribute, splitAttributeIndex, splitValue);
//        LinkedList<PlasticBranch> finalBranches = new LinkedList<>();
//        for (PlasticBranch branch: mappedTree) {
//            finalBranches.addAll(decoupleLastNode(branch));
//        }

//        modifyMappedTree(finalBranches, splitAttribute);

        PlasticNode newRoot = reassembleTree(mappedTree);
//        PlasticNode newRoot = reassembleTree(finalBranches);
        // skip step 5 in python
        // skip step 6 in python

//        for (PlasticBranch branch: finalBranches) {
//            System.out.println(branch.getDescription());
//        }

//        System.out.println(mappedTree.size());

        newRoot.splitAttribute = splitAttribute;
        newRoot.splitAttributeIndex = splitAttributeIndex;
        newRoot.setSplitTest(suggestion.splitTest);
        newRoot.updateUsedNominalAttributesInSubtree(splitAttribute, splitAttributeIndex);
        newRoot.getSuccessors().getAllSuccessors().forEach(s -> cleanupSubtree((PlasticNode) s));

        int i = 0;
        Set<SuccessorIdentifier> sortedKeys = newRoot.getSuccessors().getKeyset();
//        Collections.sort(sortedKeys);
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
        Double splitValue,
        int maxBranchLength
    ) {
        LinkedList<PlasticBranch> newBranches = new LinkedList<>();

        boolean allFinished = true;
        for (PlasticBranch branch: branches) {
            allFinished &= getEndConditionForBranch(branch, swapAttribute, swapAttributeIndex, splitValue, maxBranchLength);
        }
        if (allFinished)
            return branches;

        for (PlasticBranch branch: branches) {
            PlasticBranch linkedBranch = (PlasticBranch) branch;
            boolean branchIsFinished = getEndConditionForBranch(branch, swapAttribute, swapAttributeIndex, splitValue, maxBranchLength);
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
        return mapBranches(newBranches, swapAttribute, swapAttributeIndex, splitValue, maxBranchLength);
    }

    private boolean getEndConditionForBranch(PlasticBranch branch, Attribute swapAttribute, int swapAttributeIndex, Double splitValue, int maxBranchLength) {
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
        return !swapAttributeInChildren;
//        if (swapAttributeInChildren)
//            return false;
//        return branch.getBranchRef().size() >= maxBranchLength;
    }

    private LinkedList<PlasticBranch> mapTree(PlasticNode root, Attribute swapAttribute, Double splitValue, int swapAttributeIndex) {
        LinkedList<PlasticBranch> initialBranches = disconnectRoot(root);
        if (initialBranches == null)
            return null;
        return mapBranches(initialBranches, swapAttribute, swapAttributeIndex, splitValue, maxBranchLength);
    }

    private void modifyMappedTree(LinkedList<PlasticBranch> mappedTree, Attribute swapAttribute) {
        mappedTree.forEach(branch -> modifyBranch(branch, swapAttribute));
    }

    private void splitLastNodeIfRequiredCategorical(PlasticBranch branch,
                                    Attribute swapAttribute,
                                    int swapAttributeIndex,
                                    Double splitValue) {
        PlasticNode lastNode = branch.getLast().getNode();

        boolean shouldBeBinary = splitValue != null;
        boolean splitAttributesMatch = lastNode.splitAttribute == swapAttribute;

        if (lastNode.isLeaf() || !splitAttributesMatch) { // Option 1: the split attributes don't match
            lastNode.forceSplit(
                    swapAttribute,
                    swapAttributeIndex,
                    splitValue,
                    shouldBeBinary
            );
            if (lastNode.isLeaf()) {
                System.out.println("Error");
            }
            lastNode.successors.getAllSuccessors().forEach(s -> ((PlasticNode) s).setIsArtificial());
            return;
        }

        boolean isBinary = lastNode.successors.isBinary();

        if (!isBinary && !shouldBeBinary) // Option 2: the split attributes match and the splits are multiway (this is really the best case possible)
            // do nothing
            return;

        if (isBinary && shouldBeBinary) { // Option 3: the split attributes match and also both splits should be binary
            if (splitValue.equals(lastNode.successors.getReferenceValue()))
                // do nothing
                return;
            //TODO: we might be able to just keep everything if we have a binary nominal split
            SuccessorIdentifier defaultKey = new SuccessorIdentifier(false, splitValue, -1.0, false);
            Successors lastNodeSuccessors = new Successors(lastNode.getSuccessors(), true);
            PlasticNode oldDefaultSuccessor = (PlasticNode) lastNodeSuccessors.getSuccessorNode(defaultKey);

            Attribute lastNodeSplitAttribute = lastNode.getSplitAttribute();
            int lastNodeSplitAttributeIndex = lastNode.getSplitAttributeIndex();
            InstanceConditionalTest splitTest = lastNode.getSplitTest();
            lastNode.successors = null;
            lastNode.forceSplit(
                    swapAttribute,
                    swapAttributeIndex,
                    splitValue,
                    shouldBeBinary
            );
            PlasticNode defaultSuccessor = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(SuccessorIdentifier.DEFAULT_NOMINAL_VALUE);
            defaultSuccessor.transferSplit(
                    lastNodeSuccessors, lastNodeSplitAttribute, lastNodeSplitAttributeIndex, splitTest
            );
            return;
        }

        if (!isBinary && shouldBeBinary) { // Option 4: The split attributes match and the current split is multiway while the old one was binary. In this case, we do something similar to the numeric splits
            //TODO: we might be able to just keep everything if we have a binary nominal split
            SuccessorIdentifier defaultKey = new SuccessorIdentifier(false, splitValue, -1.0, false);
            Successors lastNodeSuccessors = new Successors(lastNode.getSuccessors(), true);
            PlasticNode oldDefaultSuccessor = (PlasticNode) lastNodeSuccessors.getSuccessorNode(defaultKey);

            Attribute lastNodeSplitAttribute = lastNode.getSplitAttribute();
            int lastNodeSplitAttributeIndex = lastNode.getSplitAttributeIndex();
            InstanceConditionalTest splitTest = lastNode.getSplitTest();
            lastNode.successors = null;
            lastNode.forceSplit(
                    swapAttribute,
                    swapAttributeIndex,
                    splitValue,
                    shouldBeBinary
            );
            PlasticNode defaultSuccessor = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(SuccessorIdentifier.DEFAULT_NOMINAL_VALUE);
            defaultSuccessor.transferSplit(
                    lastNodeSuccessors, lastNodeSplitAttribute, lastNodeSplitAttributeIndex, splitTest
            );
            return;
        }

        if (isBinary && !shouldBeBinary) { // Option 5: The split is binary but should be multiway. In this case, we force the split and then use the old subtree of the left branch of the old subtree.
            SuccessorIdentifier keyToPreviousSuccessor = new SuccessorIdentifier(false, splitValue, splitValue, false);
            PlasticNode previousSuccessor = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(keyToPreviousSuccessor);

            lastNode.successors = new Successors(shouldBeBinary, false, splitValue);
            lastNode.splitAttribute = swapAttribute;
            lastNode.splitAttributeIndex = swapAttributeIndex;

//            lastNode.successors = null;
//            lastNode.forceSplit(
//                    swapAttribute,
//                    swapAttributeIndex,
//                    splitValue,
//                    shouldBeBinary
//            );
//            lastNode.successors.removeSuccessor(keyToPreviousSuccessor);
            lastNode.successors.addSuccessor(previousSuccessor, keyToPreviousSuccessor);
            return;
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
        boolean forceSplit = lastNode.isLeaf() || lastNode.splitAttribute != swapAttribute;

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
//            System.out.println("Adjust threshold in subtree node");
//            SuccessorIdentifier leftKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, true);
//            SuccessorIdentifier rightKey = new SuccessorIdentifier(true, oldThreshold, oldThreshold, false);
//            PlasticNode succ1 = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(leftKey);
//            PlasticNode succ2 = (PlasticNode) lastNode.getSuccessors().getSuccessorNode(rightKey);
//            Successors newSuccessors = new Successors(true, true, splitValue);
//            if (succ1 != null)
//                newSuccessors.addSuccessorNumeric(splitValue, succ1, true);
//            if (succ2 != null)
//                newSuccessors.addSuccessorNumeric(splitValue, succ2, false);
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
        }

        if (Math.abs(splitValue - oldThreshold) > acceptedThresholdDeviation) {
            setRestructuredFlagInSubtree(node);
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
        if (splitAttribute != newFirstBranchElement.getNode().splitAttribute) {
            System.out.println(branch.getDescription());
        }

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

    private void modifyBranch(PlasticBranch branch, Attribute splitAttribute) {
        putLastElementToFront(branch, splitAttribute);
        resetSuccessorsInBranch(branch);
        setRestructuredFlagInBranch(branch);
        setDepth(branch);
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

    private void initialPrune(PlasticNode node) {
        if (!node.getRestructuredFlag()) {
            return;
        }
        node.resetRestructuredFlag();
        if (node.isLeaf())
            return;

        Set<SuccessorIdentifier> keys = new HashSet<>(node.getSuccessors().getKeyset());
        for (SuccessorIdentifier key : keys) {
            PlasticNode thisNode = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            if (thisNode.isArtificial() && thisNode.isLeaf()) {
                node.getSuccessors().removeSuccessor(key);
            }
            else {
                node.setIsArtificial(false);
            }
        }

        if (node.isLeaf()) {
            node.killSubtree();
            node.resetSplitAttribute();
            return;
        }

        for (SuccessorIdentifier key : node.getSuccessors().getKeyset()) {
            PlasticNode successor = (PlasticNode) node.getSuccessors().getSuccessorNode(key);
            initialPrune(successor);
        }
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
            if (thisNode.isArtificial()) {
                node.getSuccessors().removeSuccessor(key);
            }
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

    private boolean makeSamePrediction(Collection<CustomEFDTNode> nodes) {
        HashSet<Double> predictions = new HashSet<>();
        for (CustomEFDTNode node: nodes) {
            if (node.observedClassDistribution.numValues() == 0)
                continue;
            double amax = node.argmax(node.observedClassDistribution.getArrayRef());
            predictions.add(amax);
        }
        return predictions.size() <= 1;
    }
}
