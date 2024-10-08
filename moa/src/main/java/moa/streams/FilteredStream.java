/*
 *    FilteredStream.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package moa.streams;

import moa.core.Example;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import moa.options.ClassOption;
import moa.options.OptionHandler;
import moa.streams.filters.StreamFilter;
import moa.tasks.TaskMonitor;

/**
 * Class for representing a stream that is filtered.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @version $Revision: 7 $
 */
public class FilteredStream extends AbstractOptionHandler implements
        ExampleStream {

    @Override
    public String getPurposeString() {
        return "A stream that is filtered.";
    }

    private static final long serialVersionUID = 1L;

    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to filter.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public ClassOption filtersOption = new ClassOption("filters", 'f',
            "Filters to apply.", StreamFilter.class,
            "AddNoiseFilter");

    protected ExampleStream filterChain;

    @Override
    public void prepareForUseImpl(TaskMonitor monitor,
                                  ObjectRepository repository) {
        StreamFilter filters;
        monitor.setCurrentActivity("Materializing filter " //+ (i + 1)
                + "...", -1.0);
        filters = (StreamFilter) getPreparedClassOption(this.filtersOption);
        if (monitor.taskShouldAbort()) {
            return;
        }
        if (filters instanceof OptionHandler) {
            monitor.setCurrentActivity("Preparing filter " //+ (i + 1)
                    + "...", -1.0);
            ((OptionHandler) filters).prepareForUse(monitor, repository);
            if (monitor.taskShouldAbort()) {
                return;
            }
        }
        ExampleStream chain = (ExampleStream) getPreparedClassOption(this.streamOption);
        filters.setInputStream(chain);
        chain = filters;
        this.filterChain = chain;
    }

    @Override
    public long estimatedRemainingInstances() {
        return this.filterChain.estimatedRemainingInstances();
    }

    @Override
    public InstancesHeader getHeader() {
        return this.filterChain.getHeader();
    }

    @Override
    public boolean hasMoreInstances() {
        return this.filterChain.hasMoreInstances();
    }

    @Override
    public boolean isRestartable() {
        return this.filterChain.isRestartable();
    }

    @Override
    public Example nextInstance() {
        return this.filterChain.nextInstance();
    }

    @Override
    public void restart() {
        this.filterChain.restart();
    }

    @Override
    public void getDescription(StringBuilder sb, int indent) {
        // TODO Auto-generated method stub
    }
}
