// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.runtime.java.interpreted.natives.essentials.collection.iteration;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.stack.MutableStack;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionCoreInstanceWrapper;
import org.finos.legend.pure.m3.exception.PureExecutionException;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.ValueSpecificationBootstrap;
import org.finos.legend.pure.m3.navigation.valuespecification.ValueSpecification;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.interpreted.ExecutionSupport;
import org.finos.legend.pure.runtime.java.interpreted.FunctionExecutionInterpreted;
import org.finos.legend.pure.runtime.java.interpreted.VariableContext;
import org.finos.legend.pure.runtime.java.interpreted.natives.InstantiationContext;
import org.finos.legend.pure.runtime.java.interpreted.natives.NativeFunction;
import org.finos.legend.pure.runtime.java.interpreted.profiler.Profiler;

import java.util.Stack;

public class Find extends NativeFunction
{
    private final FunctionExecutionInterpreted functionExecution;

    public Find(FunctionExecutionInterpreted functionExecution, ModelRepository repository)
    {
        this.functionExecution = functionExecution;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CoreInstance execute(ListIterable<? extends CoreInstance> params, Stack<MutableMap<String, CoreInstance>> resolvedTypeParameters, Stack<MutableMap<String, CoreInstance>> resolvedMultiplicityParameters, VariableContext variableContext, MutableStack<CoreInstance> functionExpressionCallStack, Profiler profiler, InstantiationContext instantiationContext, ExecutionSupport executionSupport, Context context, ProcessorSupport processorSupport) throws PureExecutionException
    {
        CoreInstance collectionParam = params.get(0);
        ListIterable<? extends CoreInstance> collection = Instance.getValueForMetaPropertyToManyResolved(collectionParam, M3Properties.values, processorSupport);
        boolean isExecutable = ValueSpecification.isExecutable(collectionParam, processorSupport);
        switch (collection.size())
        {
            case 0:
            {
                return collectionParam;
            }
            case 1:
            {
                CoreInstance predicate = Instance.getValueForMetaPropertyToOneResolved(params.get(1), M3Properties.values, processorSupport);
                Function<CoreInstance> function = FunctionCoreInstanceWrapper.toFunction(predicate);
                CoreInstance instance = collection.get(0);
                VariableContext evalVariableContext = getParentOrEmptyVariableContextForLambda(variableContext, predicate);
                if (accept(function, instance, isExecutable, resolvedTypeParameters, resolvedMultiplicityParameters, evalVariableContext, functionExpressionCallStack, profiler, processorSupport, instantiationContext, executionSupport))
                {
                    return collectionParam;
                }
                else
                {
                    return ValueSpecificationBootstrap.wrapValueSpecification(Lists.immutable.empty(), isExecutable, processorSupport);
                }
            }
            default:
            {
                CoreInstance predicate = Instance.getValueForMetaPropertyToOneResolved(params.get(1), M3Properties.values, processorSupport);
                Function<CoreInstance> function = FunctionCoreInstanceWrapper.toFunction(predicate);
                VariableContext evalVariableContext = getParentOrEmptyVariableContextForLambda(variableContext, predicate);
                CoreInstance result = collection.detect(instance -> accept(function, instance, isExecutable, resolvedTypeParameters, resolvedMultiplicityParameters, evalVariableContext, functionExpressionCallStack, profiler, processorSupport, instantiationContext, executionSupport));
                return ValueSpecificationBootstrap.wrapValueSpecification(result, isExecutable, processorSupport);
            }
        }
    }

    private boolean accept(Function<CoreInstance> predicate, CoreInstance instance, boolean isExecutable, Stack<MutableMap<String, CoreInstance>> resolvedTypeParameters, Stack<MutableMap<String, CoreInstance>> resolvedMultiplicityParameters, VariableContext variableContext, MutableStack<CoreInstance> functionExpressionCallStack, Profiler profiler, ProcessorSupport processorSupport, InstantiationContext instantiationContext, ExecutionSupport executionSupport) throws PureExecutionException
    {
        ListIterable<CoreInstance> args = Lists.immutable.with(ValueSpecificationBootstrap.wrapValueSpecification(instance, isExecutable, processorSupport));
        CoreInstance result = this.functionExecution.executeFunction(false, predicate, args, resolvedTypeParameters, resolvedMultiplicityParameters, variableContext, functionExpressionCallStack, profiler, instantiationContext, executionSupport);
        return PrimitiveUtilities.getBooleanValue(Instance.getValueForMetaPropertyToOneResolved(result, M3Properties.values, processorSupport));
    }
}
