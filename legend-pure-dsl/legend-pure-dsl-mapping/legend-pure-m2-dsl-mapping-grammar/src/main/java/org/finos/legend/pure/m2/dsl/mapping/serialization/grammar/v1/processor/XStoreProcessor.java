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

package org.finos.legend.pure.m2.dsl.mapping.serialization.grammar.v1.processor;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.map.MapIterable;
import org.finos.legend.pure.m2.dsl.mapping.M2MappingPaths;
import org.finos.legend.pure.m2.dsl.mapping.serialization.grammar.v1.validator.MappingValidator;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.compiler.postprocessing.ProcessorState;
import org.finos.legend.pure.m3.compiler.postprocessing.ProcessorState.VariableContextScope;
import org.finos.legend.pure.m3.compiler.postprocessing.processor.Processor;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.InstanceSetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.MappingClass;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.PropertyMapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.PropertyMappingValueSpecificationContext;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.SetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.xStore.XStoreAssociationImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.xStore.XStorePropertyMapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.importstub.ImportStub;
import org.finos.legend.pure.m3.tools.matcher.Matcher;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

public class XStoreProcessor extends Processor<XStoreAssociationImplementation>
{
    @Override
    public String getClassName()
    {
        return M2MappingPaths.XStoreAssociationImplementation;
    }

    @Override
    public void process(XStoreAssociationImplementation instance, ProcessorState state, Matcher matcher, ModelRepository repository, Context context, ProcessorSupport processorSupport)
    {
        Association association = (Association) ImportStub.withImportStubByPass(instance._associationCoreInstance(), processorSupport);
        Mapping mapping = (Mapping) ImportStub.withImportStubByPass(instance._parentCoreInstance(), processorSupport);
        MapIterable<String, SetImplementation> setImpl = org.finos.legend.pure.m2.dsl.mapping.Mapping.getClassMappingsByIdIncludeEmbedded(mapping, processorSupport);

        int i = 0;
        for (PropertyMapping pm : instance._propertyMappings())
        {
            XStorePropertyMapping propertyMapping = (XStorePropertyMapping) pm;
            PropertyMappingProcessor.processPropertyMapping(propertyMapping, repository, processorSupport, association, instance);
            try (VariableContextScope ignore = state.withNewVariableContext())
            {
                InstanceSetImplementation sourceSetImpl = (InstanceSetImplementation) MappingValidator.validateId(instance, propertyMapping, setImpl, propertyMapping._sourceSetImplementationId(), "source", processorSupport);
                InstanceSetImplementation targetSetImpl = (InstanceSetImplementation) MappingValidator.validateId(instance, propertyMapping, setImpl, propertyMapping._targetSetImplementationId(), "target", processorSupport);

                Class<?> srcClass = getSetImplementationClass(sourceSetImpl, processorSupport);
                Class<?> targetClass = getSetImplementationClass(targetSetImpl, processorSupport);
                FunctionType fType = (FunctionType) ImportStub.withImportStubByPass(propertyMapping._crossExpression()._classifierGenericType()._typeArguments().getOnly()._rawTypeCoreInstance(), processorSupport);
                VariableExpression thisParam = buildParam("this", srcClass, fType.getSourceInformation(), processorSupport);
                VariableExpression thatParam = buildParam("that", targetClass, fType.getSourceInformation(), processorSupport);
                fType._parametersAddAll(Lists.immutable.with(thisParam, thatParam));
                matcher.fullMatch(propertyMapping._crossExpression(), state);

                ValueSpecification expressionSequence = propertyMapping._crossExpression()._expressionSequence().toList().getFirst();
                if (expressionSequence != null)
                {
                    PropertyMappingValueSpecificationContext usageContext = (PropertyMappingValueSpecificationContext) processorSupport.newAnonymousCoreInstance(null, M2MappingPaths.PropertyMappingValueSpecificationContext);
                    usageContext._offset(i);
                    usageContext._propertyMapping(propertyMapping);
                    expressionSequence._usageContext(usageContext);
                }
                i++;
            }
        }
    }

    private Class<?> getSetImplementationClass(InstanceSetImplementation setImplementation, ProcessorSupport processorSupport)
    {
        MappingClass<?> mappingClass = setImplementation._mappingClass();
        return mappingClass == null ? (Class<?>) ImportStub.withImportStubByPass(setImplementation._classCoreInstance(), processorSupport) : mappingClass;
    }

    private VariableExpression buildParam(String name, Class<?> type, SourceInformation sourceInfo, ProcessorSupport processorSupport)
    {
        GenericType genericType = (GenericType) org.finos.legend.pure.m3.navigation.type.Type.wrapGenericType(type, sourceInfo, processorSupport);
        VariableExpression param = (VariableExpression) processorSupport.newAnonymousCoreInstance(sourceInfo, M3Paths.VariableExpression);
        return param._name(name)
                ._genericType(genericType)
                ._multiplicity((Multiplicity) processorSupport.package_getByUserPath(M3Paths.PureOne));
    }

    @Override
    public void populateReferenceUsages(XStoreAssociationImplementation classMapping, ModelRepository repository, ProcessorSupport processorSupport)
    {
    }
}
