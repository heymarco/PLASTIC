package moa.classifiers.trees.plastic_util;

import com.yahoo.labs.samoa.instances.Attribute;
import org.w3c.dom.Attr;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Restructurer {
    private PlasticNode root;

    public Restructurer(PlasticNode root) {
        this.root = root;
    }

    private List<PlasticTreeElement> disconnectRoot() {
        Successors successors = root.getSuccessors();
        if (successors == null) {
            return null;
        }
        if (successors.size() == 0) {
            return null;
        }
        Set<SuccessorIdentifier> keys = successors.getKeyset();
        LinkedList<PlasticTreeElement> treeElements = new LinkedList<>();
        for (SuccessorIdentifier key: keys) {
            treeElements.add(new PlasticTreeElement(root, key));
        }
        return treeElements;
    }

    private List<PlasticTreeElement> disconnectSuccessors(PlasticTreeElement ancestor, Attribute swapAttribute) {
        LinkedList<PlasticTreeElement> branchExtensions = new LinkedList<>();
        PlasticNode ancestorNode = ancestor.getNode();
        SuccessorIdentifier key = ancestor.getKey();
        PlasticNode successor = ancestorNode.getSuccessors().getSuccessorNode(key);

        boolean endCondition = successor.isLeaf() || successor.getSplitAttribute() == swapAttribute;

        if (endCondition) {
            PlasticTreeElement element = new PlasticTreeElement(successor, null);
            branchExtensions.add(element);
        }
        else {
            for (SuccessorIdentifier successorSuccessorKey: successor.getSuccessors().getKeyset()) {
                PlasticTreeElement element = new PlasticTreeElement(successor, successorSuccessorKey);
                branchExtensions.add(element);
            }
        }
        return branchExtensions;
    }

    private List<List<PlasticTreeElement>> mapBranches(
        List<List<PlasticTreeElement>> branches,
        Attribute swapAttribute,
        int maxBranchLength
    ) {
        List<List<PlasticTreeElement>> newBranches = new LinkedList<>();

    }

    private boolean getEndConditionForBranch(List<PlasticTreeElement> branch, Attribute swapAttribute, int maxBranchLength) {
        PlasticNode lastNodeOfBranch = branch.getLast().getNode();
        boolean isSwapAttribute = lastNodeOfBranch.getSplitAttribute() == swapAttribute;
        boolean isLeaf = lastNodeOfBranch.isLeaf();
        if (isSwapAttribute || isLeaf)
            return true;
        boolean swapAttributeInChildren = false;
    }
}
