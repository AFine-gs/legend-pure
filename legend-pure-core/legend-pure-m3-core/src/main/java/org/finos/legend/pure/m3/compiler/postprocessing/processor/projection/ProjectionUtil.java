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

package org.finos.legend.pure.m3.compiler.postprocessing.processor.projection;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.partition.PartitionIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.compiler.postprocessing.ProcessorState;
import org.finos.legend.pure.m3.compiler.unload.Unbinder;
import org.finos.legend.pure.m3.compiler.unload.unbind.UnbindState;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PropertyOwner;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.AnnotatedElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Stereotype;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Tag;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.TaggedValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.AbstractProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Any;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassProjection;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.FunctionExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.InstanceValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.importstub.ImportStub;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

public class ProjectionUtil
{
    public static Property<?, ?> deepCopyAndBindSimpleProperty(Property<?, ?> originalProperty, ClassProjection<?> projection, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport)
    {
        Property<?, ?> propertyCopy = createPropertyCopy(originalProperty, projection, modelRepository, processorSupport);
        MutableList<GenericType> typeArgumentsCopy = Lists.mutable.withAll(propertyCopy._classifierGenericType()._typeArguments());
        GenericType newOwnerType = projection._classifierGenericType()._typeArguments().getOnly();
        typeArgumentsCopy.set(0, (GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(newOwnerType, propertyCopy.getSourceInformation(), processorSupport));
        propertyCopy._classifierGenericType()._typeArguments(typeArgumentsCopy);
        context.update(propertyCopy);
        return propertyCopy;
    }

    public static QualifiedProperty<?> deepCopyAndBindQualifiedProperty(QualifiedProperty<?> originalProperty, ClassProjection<?> projection, ProcessorState state, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport)
    {
        QualifiedProperty<?> propertyCopy = createQualifiedPropertyCopy(originalProperty, projection, modelRepository, processorSupport);

        if (originalProperty._functionName() != null)
        {
            propertyCopy._functionName(originalProperty._functionName());
        }
        else if (propertyCopy._functionName() != null)
        {
            propertyCopy._functionNameRemove();
        }

        GenericType ownerType = projection._classifierGenericType()._typeArguments().getOnly();

        FunctionType classifierFunctionType = (FunctionType) propertyCopy._classifierGenericType()._typeArguments().getOnly()._rawType();

        VariableExpression propertyOwner = classifierFunctionType._parameters().getFirst();

        propertyOwner._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(ownerType, propertyCopy.getSourceInformation(), processorSupport));
        classifierFunctionType._functionCoreInstance(Lists.immutable.of(propertyCopy));

        MutableMap<CoreInstance, CoreInstance> processedMap = Maps.mutable.empty();
        propertyCopy._expressionSequence(originalProperty._expressionSequence().collect(expr -> copyValueSpecification(expr, modelRepository, context, processorSupport, processedMap, state, projection)));

        return propertyCopy;
    }

    @Deprecated
    public static AbstractProperty<?> createPropertyCopy(AbstractProperty<?> sourceProperty, PropertyOwner newOwner, ModelRepository repository, ProcessorSupport processorSupport)
    {
        return (sourceProperty instanceof Property) ?
               createPropertyCopy((Property<?, ?>) sourceProperty, newOwner, repository, processorSupport) :
               createAbstractPropertyCopy(sourceProperty, newOwner, repository, processorSupport);
    }

    public static Property<?, ?> createPropertyCopy(Property<?, ?> sourceProperty, PropertyOwner newOwner, ModelRepository repository, ProcessorSupport processorSupport)
    {
        Property<?, ?> copy = createAbstractPropertyCopy(sourceProperty, newOwner, repository, processorSupport);
        if (sourceProperty._aggregation() != null)
        {
            copy._aggregation(sourceProperty._aggregation());
        }
        else if (copy._aggregation() != null)
        {
            copy._aggregationRemove();
        }
        return copy;
    }

    public static QualifiedProperty<?> createQualifiedPropertyCopy(QualifiedProperty<?> qualifiedProperty, PropertyOwner newOwner, ModelRepository repository, ProcessorSupport processorSupport)
    {
        return createAbstractPropertyCopy(qualifiedProperty, newOwner, repository, processorSupport);
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractProperty<?>> T createAbstractPropertyCopy(T sourceProperty, PropertyOwner newOwner, ModelRepository repository, ProcessorSupport processorSupport)
    {
        SourceInformation sourceInfo = newOwner.getSourceInformation();
        T copy = (T) repository.newCoreInstance(sourceProperty.getName(), sourceProperty.getClassifier(), sourceInfo);
        copy._owner(newOwner);
        if (sourceProperty._name() != null)
        {
            copy._name(sourceProperty._name());
        }
        else if (copy._name() != null)
        {
            copy._nameRemove();
        }
        copy._classifierGenericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(sourceProperty._classifierGenericType(), sourceInfo, processorSupport));
        copy._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(sourceProperty._genericType(), sourceInfo, processorSupport));
        copy._multiplicity((Multiplicity) org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.copyMultiplicity(sourceProperty._multiplicity(), false, processorSupport));

        copyAnnotations(sourceProperty, copy, false, processorSupport);
        return copy;
    }

    public static void copyAnnotations(AnnotatedElement originalOwner, AnnotatedElement newOwner, boolean updateModelElements, ProcessorSupport processorSupport)
    {
        copyAnnotations(originalOwner, newOwner, newOwner.getSourceInformation(), updateModelElements, processorSupport);
    }

    public static void copyAnnotations(AnnotatedElement originalOwner, AnnotatedElement newOwner, SourceInformation newSourceInfo, boolean updateModelElements, ProcessorSupport processorSupport)
    {
        ListIterable<? extends CoreInstance> stereotypes = ImportStub.withImportStubByPasses(ListHelper.wrapListIterable(originalOwner._stereotypesCoreInstance()), processorSupport);
        if (stereotypes.notEmpty())
        {
            newOwner._stereotypesAddAllCoreInstance(stereotypes);
            if (updateModelElements)
            {
                stereotypes.forEach(st -> ((Stereotype) st)._modelElementsAdd(newOwner));
            }
        }

        RichIterable<? extends TaggedValue> taggedValues = originalOwner._taggedValues();
        if (taggedValues.notEmpty())
        {
            taggedValues.forEach(taggedValue ->
            {
                Tag tag = (Tag) ImportStub.withImportStubByPass(taggedValue._tagCoreInstance(), processorSupport);
                String value = taggedValue._value();

                TaggedValue taggedValueCopy = (TaggedValue) processorSupport.newAnonymousCoreInstance(newSourceInfo, M3Paths.TaggedValue);
                taggedValueCopy._tagCoreInstance(tag);
                taggedValueCopy._value(value);
                newOwner._taggedValuesAdd(taggedValueCopy);
            });

            if (updateModelElements)
            {
                MutableSet<CoreInstance> tags = Sets.mutable.empty();
                taggedValues.asLazy()
                        .collect(tv -> (Tag) ImportStub.withImportStubByPass(tv._tagCoreInstance(), processorSupport))
                        .select(tags::add)
                        .forEach(tag -> tag._modelElementsAdd(newOwner));
            }
        }
    }

    public static void removeCopiedAnnotations(AnnotatedElement originalOwner, AnnotatedElement newOwner, ProcessorSupport processorSupport)
    {
        ListIterable<? extends Stereotype> originalStereotypes = (ListIterable<? extends Stereotype>) ImportStub.withImportStubByPasses((ListIterable<? extends CoreInstance>) originalOwner._stereotypesCoreInstance(), processorSupport);
        if (originalStereotypes.notEmpty())
        {
            RichIterable<? extends CoreInstance> stereotypesToPartition = newOwner._stereotypesCoreInstance();
            PartitionIterable<? extends CoreInstance> partition = stereotypesToPartition.partition(originalStereotypes.toSet()::contains);

            RichIterable<? extends Stereotype> stereotypesToRemove = (RichIterable<? extends Stereotype>) partition.getSelected();
            RichIterable<? extends CoreInstance> stereotypesToKeep = partition.getRejected();
            for (Stereotype st : stereotypesToRemove)
            {
                st._modelElementsRemove(newOwner);
                if (st._modelElements().isEmpty())
                {
                    st._modelElementsRemove();
                }
            }
            newOwner._stereotypesCoreInstance(stereotypesToKeep);
        }

        RichIterable<? extends TaggedValue> originalTaggedValues = originalOwner._taggedValues();

        if (originalTaggedValues.notEmpty())
        {
            RichIterable<String> originalTaggedValuesAsString = originalTaggedValues.collect(tv ->
            {
                Tag tag = (Tag) ImportStub.withImportStubByPass(tv._tagCoreInstance(), processorSupport);
                String value = tv._value();
                return tag.getName() + value;
            });

            RichIterable<? extends TaggedValue> taggedValuesToPartition = newOwner._taggedValues();

            PartitionIterable<? extends TaggedValue> partition = taggedValuesToPartition.partition(tv ->
            {
                Tag tag = (Tag) ImportStub.withImportStubByPass(tv._tagCoreInstance(), processorSupport);
                String value = tv._value();
                return originalTaggedValuesAsString.contains(tag.getName() + value);
            });
            RichIterable<? extends TaggedValue> taggedValuesToRemove = partition.getSelected();
            RichIterable<? extends TaggedValue> taggedValuesToKeep = partition.getRejected();

            for (TaggedValue taggedValue : taggedValuesToRemove)
            {
                Tag tag = (Tag) ImportStub.withImportStubByPass(taggedValue._tagCoreInstance(), processorSupport);
                tag._modelElementsRemove(newOwner);
                if (tag._modelElements().isEmpty())
                {
                    tag._modelElementsRemove();
                }
            }
            newOwner._taggedValues(taggedValuesToKeep);
        }
    }

    private static ValueSpecification copyValueSpecification(ValueSpecification valueSpecification, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, ProcessorState state, ClassProjection<?> classProjection)
    {
        return copyValueSpecification(valueSpecification, modelRepository, context, processorSupport, Maps.mutable.empty(), state, classProjection);
    }

    private static ValueSpecification copyValueSpecification(ValueSpecification valueSpecification, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection)
    {
        ValueSpecification copy = (ValueSpecification) processedMap.get(valueSpecification);
        if (copy == null)
        {
            copy = (ValueSpecification) makeCopyAndProcessGenericProperties(valueSpecification, modelRepository, processorSupport, classProjection);
            if (valueSpecification instanceof InstanceValue)
            {
                copyInstanceValue((InstanceValue) valueSpecification, modelRepository, context, processorSupport, processedMap, state, classProjection, (InstanceValue) copy);
            }
            else if (valueSpecification instanceof FunctionExpression)
            {
                copyFunctionExpression((FunctionExpression) valueSpecification, modelRepository, context, processorSupport, processedMap, state, classProjection, (FunctionExpression) copy);
            }
            else if (valueSpecification instanceof VariableExpression)
            {
                copyVariableExpression((VariableExpression) valueSpecification, (VariableExpression) copy);
            }
            else if (valueSpecification instanceof LambdaFunction)
            {
                copyLambdaFunction((LambdaFunction<?>) valueSpecification, modelRepository, context, processorSupport, processedMap, state, classProjection, (LambdaFunction<?>) copy);
            }
            else if (valueSpecification instanceof FunctionDefinition)
            {
                copyFunctionDefinition((FunctionDefinition<?>) valueSpecification, modelRepository, context, processorSupport, processedMap, state, classProjection, (FunctionDefinition<?>) copy);
            }
            processedMap.put(valueSpecification, copy);

            Unbinder.process(Sets.immutable.with(copy), modelRepository, state.getParserLibrary(), state.getInlineDSLLibrary(), context, processorSupport, new UnbindState(context, state.getURLPatternLibrary(), state.getInlineDSLLibrary(), processorSupport), state.getMessage());
        }
        return copy;
    }

    private static CoreInstance makeCopyAndProcessGenericProperties(Any instance, ModelRepository modelRepository, ProcessorSupport processorSupport, ClassProjection<?> classProjection)
    {
        Any copy = (Any) modelRepository.newAnonymousCoreInstance(classProjection.getSourceInformation(), instance.getClassifier());
        if (instance instanceof AbstractProperty)
        {
            if (((AbstractProperty<?>) instance)._multiplicity() != null)
            {
                ((AbstractProperty<?>) copy)._multiplicity(((AbstractProperty<?>) instance)._multiplicity());
            }
            else if (((AbstractProperty<?>) copy)._multiplicity() != null)
            {
                ((AbstractProperty<?>) copy)._multiplicityRemove();
            }
        }
        if (instance instanceof AbstractProperty && ((AbstractProperty<?>) instance)._genericType() != null)
        {
            ((AbstractProperty<?>) copy)._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(((AbstractProperty<?>) instance)._genericType(), copy.getSourceInformation(), processorSupport));
        }
        if (instance instanceof ValueSpecification && ((ValueSpecification) instance)._genericType() != null)
        {
            ((ValueSpecification) copy)._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(((ValueSpecification) instance)._genericType(), copy.getSourceInformation(), processorSupport));
        }
        if (instance._classifierGenericType() != null)
        {
            copy._classifierGenericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(instance._classifierGenericType(), copy.getSourceInformation(), processorSupport));
        }
        return copy;
    }

    private static void copyInstanceValue(InstanceValue valueSpecification, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection, InstanceValue copy)
    {
        copy._values(valueSpecification._valuesCoreInstance().collect(value -> copyInstanceValueValue(value, modelRepository, context, processorSupport, processedMap, state, classProjection)));
    }

    private static CoreInstance copyInstanceValueValue(CoreInstance value, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection)
    {
        if (Instance.instanceOf(value, M3Paths.Function, processorSupport))
        {
            return copyFunction((Function<?>) value, modelRepository, processorSupport, classProjection, processedMap, state, context);
        }
        if (!Instance.instanceOf(value, M3Paths.ValueSpecification, processorSupport))
        {
            return value;
        }
        return copyValueSpecification((ValueSpecification) value, modelRepository, context, processorSupport, processedMap, state, classProjection);
    }

    private static Function<?> copyFunction(Function<?> function, ModelRepository modelRepository, ProcessorSupport processorSupport, ClassProjection<?> classProjection, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, Context context)
    {
        Function<?> copy = (Function<?>) processedMap.get(function);
        if (copy == null)
        {
            copy = (Function<?>) makeCopyAndProcessGenericProperties(function, modelRepository, processorSupport, classProjection);
            if (function instanceof LambdaFunction)
            {
                copyLambdaFunction((LambdaFunction<?>) function, modelRepository, context, processorSupport, processedMap, state, classProjection, (LambdaFunction<?>) copy);
            }
            else if (function instanceof FunctionDefinition)
            {
                copyFunctionDefinition((FunctionDefinition<?>) function, modelRepository, context, processorSupport, processedMap, state, classProjection, (FunctionDefinition<?>) copy);
            }
            processedMap.put(function, copy);

            Unbinder.process(Sets.immutable.with(copy), modelRepository, state.getParserLibrary(), state.getInlineDSLLibrary(), context, processorSupport, new UnbindState(context, state.getURLPatternLibrary(), state.getInlineDSLLibrary(), processorSupport), state.getMessage());
        }
        return copy;
    }

    private static void copyFunctionDefinition(FunctionDefinition<?> valueSpecification, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection, FunctionDefinition<?> copy)
    {
        valueSpecification._expressionSequence().forEach(exp -> copy._expressionSequenceAdd(copyValueSpecification(exp, modelRepository, context, processorSupport, processedMap, state, classProjection)));
    }

    private static void copyLambdaFunction(LambdaFunction<?> lambda, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection, LambdaFunction<?> copy)
    {
        copy._openVariablesAddAll(lambda._openVariables());
        FunctionType functionType = (FunctionType) copy._classifierGenericType()._typeArguments().getOnly()._rawTypeCoreInstance();
        functionType._parameters().forEach(parameter ->
        {
            if (parameter._genericType() != null)
            {
                parameter._genericTypeRemove();
                parameter._multiplicityRemove();
            }
        });
        copyFunctionDefinition(lambda, modelRepository, context, processorSupport, processedMap, state, classProjection, copy);
    }

    private static void copyVariableExpression(VariableExpression valueSpecification, VariableExpression copy)
    {
        if (valueSpecification._name() != null)
        {
            copy._name(valueSpecification._name());
        }
        else if (copy._name() != null)
        {
            copy._nameRemove();
        }
        copy._classifierGenericTypeRemove();
        copy._genericTypeRemove();
    }

    private static void copyFunctionExpression(FunctionExpression valueSpecification, ModelRepository modelRepository, Context context, ProcessorSupport processorSupport, MutableMap<CoreInstance, CoreInstance> processedMap, ProcessorState state, ClassProjection<?> classProjection, FunctionExpression copy)
    {
        valueSpecification._parametersValues().forEach(p -> copy._parametersValuesAdd(copyValueSpecification(p, modelRepository, context, processorSupport, processedMap, state, classProjection)));

        if (valueSpecification._importGroup() != null)
        {
            copy._importGroup(valueSpecification._importGroup());
        }
        else if (copy._importGroup() != null)
        {
            copy._importGroupRemove();
        }

        if (valueSpecification._functionName() != null)
        {
            copy._functionName(valueSpecification._functionName());
        }
        else if (copy._functionName() != null)
        {
            copy._functionNameRemove();
        }

        if (valueSpecification._propertyName() != null)
        {
            InstanceValue propertyNameSource = valueSpecification._propertyName();
            InstanceValue propertyNameCopy = (InstanceValue) copyValueSpecification(propertyNameSource, modelRepository, context, processorSupport, state, classProjection);

            propertyNameCopy._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(propertyNameSource._genericType(), processorSupport));
            propertyNameCopy._multiplicity((Multiplicity) org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.copyMultiplicity(propertyNameSource._multiplicity(), false, processorSupport));

            copy._propertyName(propertyNameCopy);
        }
        if (valueSpecification._qualifiedPropertyName() != null)
        {
            InstanceValue propertyNameSource = valueSpecification._qualifiedPropertyName();
            InstanceValue qualifiedPropertyNameCopy = (InstanceValue) copyValueSpecification(propertyNameSource, modelRepository, context, processorSupport, state, classProjection);

            qualifiedPropertyNameCopy._genericType((GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(propertyNameSource._genericType(), processorSupport));
            qualifiedPropertyNameCopy._multiplicity((Multiplicity) org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.copyMultiplicity(propertyNameSource._multiplicity(), false, processorSupport));

            copy._qualifiedPropertyName(qualifiedPropertyNameCopy);
        }
    }

}
