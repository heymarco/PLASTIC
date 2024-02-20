package moa.classifiers.trees.plastic_util;

import java.util.LinkedList;

public class PlasticBranch {
    private LinkedList<PlasticTreeElement> branch = new LinkedList<>();

    public PlasticBranch(){}

    public PlasticBranch(LinkedList<PlasticTreeElement> branch) {
        this.branch = branch;
    }

    public LinkedList<PlasticTreeElement> getBranchRef() {
        return branch;
    }

    public String getDescription() {
        if (branch == null) {
            return "Branch is null";
        }
        StringBuilder s = new StringBuilder();
        int i = 0;
        for (PlasticTreeElement e: branch) {
            i++;
            s.append(e.getDescription()).append(i == branch.size() ? "" : " --> ");
        }
        return s.toString();
    }

    public PlasticTreeElement getLast() {
        if (branch == null)
            return null;
        if (branch.size() == 0) {
            return null;
        }
        return branch.getLast();
    }

    public PlasticBranch copy() {
        PlasticBranch cpy = new PlasticBranch();
        for (PlasticTreeElement item: branch) {
            cpy.getBranchRef().add(item.copy());
        }
        return cpy;
    }
}
