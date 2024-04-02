package moa.streams;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import moa.MOAObject;
import moa.core.InstanceExample;
import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import moa.tasks.TaskMonitor;

import java.util.LinkedList;

public class QueueStream extends AbstractOptionHandler implements ExampleStream {

    private LinkedList<InstanceExample> queue = new LinkedList<>();

    void addToQueue(Instance newInstance) {
        queue.add(new InstanceExample(newInstance));
    }


    @Override
    public int measureByteSize() {
        return 0;
    }

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {}

    @Override
    public void getDescription(StringBuilder sb, int indent) {

    }

    @Override
    public InstancesHeader getHeader() {
        return null;
    }

    @Override
    public long estimatedRemainingInstances() {
        return queue.size();
    }

    @Override
    public boolean hasMoreInstances() {
        return queue.size() > 0;
    }

    @Override
    public InstanceExample nextInstance() {
        return queue.removeFirst();
    }

    @Override
    public boolean isRestartable() {
        return true;
    }

    @Override
    public void restart() {
        queue = new LinkedList<>();
    }
}
