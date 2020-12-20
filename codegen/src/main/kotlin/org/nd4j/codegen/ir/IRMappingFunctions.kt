package org.nd4j.codegen.ir

import org.nd4j.ir.MapperNamespace
import org.nd4j.ir.OpNamespace.*
import org.nd4j.ir.TensorNamespace
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.shade.protobuf.GeneratedMessageV3
import org.nd4j.shade.protobuf.ProtocolMessageEnum

abstract class BaseAttributeExtractionRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(
    name: String,
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>):
    AttributeMappingRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        where DATA_TYPE: ProtocolMessageEnum {

    protected var opDescriptor: OpDescriptor? = null
    protected val mappingNamesToPerform = mappingNamesToPerform
    protected val transformerArgs = transformerArgs
    protected val name = name


    override fun initWithMappingProcess(mappingProcess: MappingProcess<GRAPH_DEF,OP_DEF_TYPE, NODE_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>) {

    }

    override fun mappingNamesToPerform(): Map<String, String> {
        return mappingNamesToPerform
    }

    override fun name(): String {
        return name
    }

    override fun mappingTransformerArgs(): Map<String, List<ArgDescriptor>> {
        return transformerArgs
    }


    abstract fun createIRAttribute(name: String, attrDef: ATTR_DEF, attributeValueType: ATTR_VALUE_TYPE): IRAttribute<ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>


    override fun serialize(): MapperNamespace.MappingRule {
        val builder = MapperNamespace.MappingRule.newBuilder()
        builder.ruleName = name()
        builder.functionName = name()
        val descriptorList = opDescriptor!!.argDescriptorList
        /**
         * TODO: add index mapping by doing a list lookup of what value
         * is present in the key. Each final arg descriptor can be used to
         * look up the intended index of the argument.
         */
        for ((k, v) in transformerArgs) {
            val filteredList = descriptorList.filter { input -> input.name == k }
            require(filteredList.isNotEmpty()) { "Output attribute " + k + " was not found in op descriptor " + name() + " list of attribtues was " + descriptorList.map { input -> input.name } }

            val descriptor = filteredList[0]
            when (descriptor.argType) {
                ArgDescriptor.ArgType.BOOL -> builder.addOutputBooleanName(k)
                ArgDescriptor.ArgType.INT64 -> builder.addOutputIntName(k)
                ArgDescriptor.ArgType.FLOAT -> builder.addOutputFloatName(k)
                ArgDescriptor.ArgType.DOUBLE -> builder.addOutputDoubleName(k)
                ArgDescriptor.ArgType.INT64 -> builder.addOutputIntName(k)
                ArgDescriptor.ArgType.INPUT_TENSOR -> builder.addInputTensorName(k)
                ArgDescriptor.ArgType.OUTPUT_TENSOR -> builder.addOutputTensorName(k)
            }

            for (associatedInput in v) {
                when (associatedInput.argType) {
                    AttributeValueType.STRING -> builder.addInputStringAttrName(associatedInput.name)
                    AttributeValueType.BOOL -> builder.addInputBooleanName(associatedInput.name)
                    AttributeValueType.FLOAT -> builder.addInputFloatName(associatedInput.name)
                    AttributeValueType.INT -> builder.addInputIntName(associatedInput.name)
                    AttributeValueType.TENSOR -> builder.addInputTensorName(associatedInput.name)
                }
            }


        }


        return builder.build()
    }

    override fun argDescriptorTypesForOutputName(
        name: String, mappingProcess:
        MappingProcess<GRAPH_DEF,OP_DEF_TYPE, NODE_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor.ArgType> {
        val nd4jOpDescriptor = nd4jOpDescriptors.findOp(mappingProcess.opName())
        val names = nd4jOpDescriptor.argDescriptorList.map { input -> input.name }
        if(!names.contains(name)) {
            throw java.lang.IllegalArgumentException("Unable to find name $name for op $nd4jOpDescriptor.name")
        }

        return nd4jOpDescriptor.argDescriptorList.filter { argDescriptor -> argDescriptor.name == name }.map { argDescriptor -> argDescriptor.argType}
    }
}

abstract class StringEqualsAdapterRule<
        GRAPH_DEF :GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(
    mappingNamesToPerform: Map<String, String> = emptyMap(),
    transformerArgs: Map<String, List<ArgDescriptor>> = emptyMap()):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "stringequals",
        mappingNamesToPerform =  mappingNamesToPerform,
        transformerArgs = transformerArgs)
        where DATA_TYPE: ProtocolMessageEnum {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.BOOL) || argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for((k, v) in mappingNamesToPerform()) {
            val descriptorForName = transformerArgs[k]
            val compString = descriptorForName!![0].stringValue
            val testValue = mappingCtx.irAttributeValueForNode(v).stringValue()
            val descriptorBuilder = ArgDescriptor.newBuilder()
            descriptorBuilder.name = v
            val argDescriptorType =  mappingCtx.argDescriptorTypeForName(k)
            descriptorBuilder.argType = argDescriptorType
            descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = argDescriptorType
            )

            when(argDescriptorType) {
                ArgDescriptor.ArgType.BOOL -> {
                    descriptorBuilder.boolValue = testValue == compString
                }

                ArgDescriptor.ArgType.INT64 -> {
                    descriptorBuilder.int64Value = if (testValue == compString) 1 else 0

                }
            }

            ret.add(descriptorBuilder.build())

        }
        return ret
    }
}


abstract class StringContainsAdapterRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(
    mappingNamesToPerform: Map<String, String> = emptyMap(),
    transformerArgs: Map<String, List<ArgDescriptor>> = emptyMap()):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "stringcontains",
        mappingNamesToPerform =  mappingNamesToPerform,
        transformerArgs = transformerArgs)
        where DATA_TYPE: ProtocolMessageEnum {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.BOOL) || argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for((k, v) in mappingNamesToPerform()) {
            val descriptorForName = transformerArgs[k]
            val compString = descriptorForName!![0].stringValue
            val testValue = mappingCtx.irAttributeValueForNode(v).stringValue()
            val descriptorBuilder = ArgDescriptor.newBuilder()
            val argDescriptorType =  mappingCtx.argDescriptorTypeForName(k)
            descriptorBuilder.name = k
            descriptorBuilder.argType =  mappingCtx.argDescriptorTypeForName(k)
            descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = argDescriptorType
            )

            when(argDescriptorType) {
                ArgDescriptor.ArgType.BOOL -> {
                    descriptorBuilder.boolValue = compString.contains(testValue)
                }

                ArgDescriptor.ArgType.INT64 -> {
                    descriptorBuilder.int64Value = if (compString.contains(testValue)) 1 else 0

                }

            }
            ret.add(descriptorBuilder.build())

        }
        return ret
    }
}

abstract class StringNotEqualsAdapterRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(
    mappingNamesToPerform: Map<String, String> = emptyMap(),
    transformerArgs: Map<String, List<ArgDescriptor>> = emptyMap()):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "stringnotequalsadapterrule",
        mappingNamesToPerform =  mappingNamesToPerform,
        transformerArgs = transformerArgs)
        where DATA_TYPE: ProtocolMessageEnum {

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        /**
         * TODO: add index mapping by doing a list lookup of what value
         * is present in the key. Each final arg descriptor can be used to
         * look up the intended index of the argument.
         */
        for((k, v) in mappingNamesToPerform()) {
            val descriptorForName = transformerArgs[k]
            val compString = descriptorForName!![0].stringValue
            val testValue = mappingCtx.irAttributeValueForNode(v).stringValue()
            when(mappingCtx.argDescriptorTypeForName(k)) {
                ArgDescriptor.ArgType.INT64 -> {
                    ret.add(ArgDescriptor {
                        name = k
                        argType = mappingCtx.argDescriptorTypeForName(k)
                        int64Value = if(testValue != compString) 1 else 0
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INT64
                        )

                    })
                }

                ArgDescriptor.ArgType.BOOL -> {
                    ret.add(ArgDescriptor {
                        name = k
                        argType = mappingCtx.argDescriptorTypeForName(k)
                        boolValue = testValue != compString
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.BOOL
                        )

                    })
                }
            }

        }
        return ret
    }

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.BOOL) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }
}

abstract class SizeThresholdIntArrayIntIndexRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(mappingNamesToPerform: Map<String, String>,
                                                     transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "sizethresholdarrayint", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) where DATA_TYPE: ProtocolMessageEnum {



    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        /**
         * TODO: add index mapping by doing a list lookup of what value
         * is present in the key. Each final arg descriptor can be used to
         * look up the intended index of the argument.
         */
        for((k, v) in mappingNamesToPerform()) {
            val descriptorForName = transformerArgs[k]
            val inputArr = mappingCtx.irAttributeValueForNode(v).listIntValue()
            val index = descriptorForName!![0].int32Value
            val sizeThreshold = descriptorForName!![1].int64Value
            val fallbackIndex = descriptorForName!![2].stringValue
            val descriptorBuilder = ArgDescriptor.newBuilder()
            descriptorBuilder.name = v
            descriptorBuilder.argType = ArgDescriptor.ArgType.INT64
            if(inputArr.size < sizeThreshold) {
                descriptorBuilder.int64Value = inputArr[fallbackIndex.toInt()]
            } else {
                descriptorBuilder.int64Value = inputArr[index]
            }

            descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = ArgDescriptor.ArgType.INT64
            )


            ret.add(descriptorBuilder.build())

        }
        return ret
    }

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.INT ||
                argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }
}

abstract class ConditionalFieldValueIntIndexArrayRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(mappingNamesToPerform: Map<String, String>,
                                                     transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "conditionalfieldvalueintindex", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs)
        where  DATA_TYPE: ProtocolMessageEnum {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.STRING || argDescriptorType ==AttributeValueType.INT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for((k, v) in mappingNamesToPerform()) {
            val listOfArgs  = transformerArgs[k]
            val inputArr = mappingCtx.irAttributeValueForNode(listOfArgs!![3].stringValue).listIntValue()
            val trueIndex = listOfArgs!![1].int32Value
            val falseIndex = listOfArgs!![2].int32Value
            val targetValueToTest = listOfArgs!![0].stringValue
            val testValue = mappingCtx.irAttributeValueForNode(v).stringValue()
            val intValueToSet = if (testValue == targetValueToTest)  inputArr[trueIndex] else inputArr[falseIndex]
            ret.add(ArgDescriptor {
                name  = v
                int64Value = intValueToSet
                argType = ArgDescriptor.ArgType.INT64
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INT64
                )
            })

        }
        return ret
    }
}


abstract class ConditionalFieldValueIntIndexNDArrayRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(mappingNamesToPerform: Map<String, String>,
                                                     transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "conditionalfieldvalueintindexndarray", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs)
        where  DATA_TYPE: ProtocolMessageEnum {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR || argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for((k, v) in mappingNamesToPerform()) {
            val listOfArgs  = transformerArgs[k]
            val inputArr = mappingCtx.tensorInputFor(listOfArgs!![3].stringValue).toNd4jNDArray().ravel()
            val trueIndex = listOfArgs!![1].int32Value
            val falseIndex = listOfArgs!![2].int32Value
            val targetValueToTest = listOfArgs!![0].stringValue
            val testValue = mappingCtx.irAttributeValueForNode(v).stringValue()
            val intValueToSet = if (testValue == targetValueToTest)  inputArr.getInt(trueIndex) else inputArr.getInt(falseIndex)
            ret.add(ArgDescriptor {
                name  = v
                int64Value = intValueToSet.toLong()
                argType = ArgDescriptor.ArgType.INT64
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INT64
                )
            })

        }
        return ret
    }
}


/**
 * Need to implement tensor size extraction value at index
 */


abstract class NDArraySizeAtRule<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE>(mappingNamesToPerform: Map<String, String>,
                                                     transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "ndarraysizeat", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs)
        where  DATA_TYPE: ProtocolMessageEnum {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        mappingNamesToPerform().forEach { (k, v) ->
            val transformArgsForAttribute = transformerArgs[k]
            //note that this finds a value for a named tensor within either the graph or the node
            //some frameworks may have a value node with a value attribute
            //others may have the actual tensor value
            val inputArr = mappingCtx.tensorInputFor(v)
            val sizeIndex = transformArgsForAttribute!![0].int32Value
            val sizeAt = inputArr.shape()[sizeIndex]
            val argDescriptor = ArgDescriptor {
                name = v
                argType = ArgDescriptor.ArgType.INT64
                int64Value = sizeAt
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INT64
                )
            }
            ret.add(argDescriptor)
        }

        return ret
    }
}


/**
 * Need to implement tensor size extraction value at index
 */


abstract class ValueMapping<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE: ProtocolMessageEnum>(mappingNamesToPerform: Map<String, String>,
                                                                          transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "valuemapping", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType != AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return !argDescriptorType.containsAll(listOf(ArgDescriptor.ArgType.INPUT_TENSOR,
            ArgDescriptor.ArgType.OUTPUT_TENSOR,ArgDescriptor.ArgType.DATA_TYPE))
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF,NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE,DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for((k, v) in mappingNamesToPerform()) {
            val descriptorBuilder = ArgDescriptor.newBuilder()
            descriptorBuilder.name = k
            val op = nd4jOpDescriptors.findOp(mappingCtx.nd4jOpName())
            val irAttribute = mappingCtx.irAttributeValueForNode(v)
            when(irAttribute.attributeValueType()) {
                AttributeValueType.INT -> {
                    descriptorBuilder.argType = ArgDescriptor.ArgType.INT64
                    descriptorBuilder.int64Value = irAttribute.intValue()
                    descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.INT64
                    )

                }

                AttributeValueType.FLOAT -> {
                    descriptorBuilder.argType = ArgDescriptor.ArgType.DOUBLE
                    descriptorBuilder.floatValue = irAttribute.floatValue()
                    descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.DOUBLE
                    )

                }

                AttributeValueType.BOOL -> {
                    descriptorBuilder.argType =  ArgDescriptor.ArgType.BOOL
                    descriptorBuilder.boolValue = irAttribute.boolValue()
                    descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.BOOL
                    )
                }

                AttributeValueType.STRING -> {
                    descriptorBuilder.argType =  ArgDescriptor.ArgType.STRING
                    descriptorBuilder.stringValue = irAttribute.stringValue()
                    descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.STRING
                    )
                }

                AttributeValueType.DATA_TYPE -> {
                    descriptorBuilder.argType = ArgDescriptor.ArgType.DATA_TYPE
                    descriptorBuilder.dataTypeValue = irAttribute.dataTataTypeValue().nameSpaceDataType()
                    descriptorBuilder.argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.DATA_TYPE
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unable to map value $k. Please use different rule for list values and tensors.")
                }
            }


            ret.add(descriptorBuilder.build())

        }
        return ret
    }
}


abstract class NumberToBoolean<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE: ProtocolMessageEnum>(mappingNamesToPerform: Map<String, String>,
                                                                          transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "booleantonumber", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.INT || argDescriptorType == AttributeValueType.FLOAT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.BOOL)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for ((k, v) in mappingNamesToPerform()) {
            val descriptorBuilder = ArgDescriptor.newBuilder()
            descriptorBuilder.name = k
            val irAttribute = mappingCtx.irAttributeValueForNode(v)
            val targetIdx = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = ArgDescriptor.ArgType.BOOL
            )

            if(targetIdx < 0) {
                throw java.lang.IllegalArgumentException("Output attribute $k not found with boolean type for op name ${mappingCtx.nd4jOpName()} and input op name ${mappingCtx.opName()}")
            }


            descriptorBuilder.argIndex = targetIdx
            descriptorBuilder.argType = ArgDescriptor.ArgType.BOOL


            when(irAttribute.attributeValueType()) {
                AttributeValueType.FLOAT -> {
                    descriptorBuilder.boolValue = irAttribute.floatValue() > 0
                }
                AttributeValueType.INT -> {
                    descriptorBuilder.boolValue = irAttribute.intValue() > 0
                }
            }

            ret.add(descriptorBuilder.build())
        }
        return ret
    }
}



abstract class BooleanToNumber<
        GRAPH_DEF: GeneratedMessageV3,
        OP_DEF_TYPE: GeneratedMessageV3,
        NODE_TYPE: GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE: ProtocolMessageEnum>(mappingNamesToPerform: Map<String, String>,
                                                                          transformerArgs: Map<String, List<ArgDescriptor>>):
    BaseAttributeExtractionRule<GRAPH_DEF,OP_DEF_TYPE,NODE_TYPE,ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "booleantonumber", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.INT || argDescriptorType == AttributeValueType.BOOL
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.DOUBLE)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for ((k, v) in mappingNamesToPerform()) {
            val descriptorBuilder = ArgDescriptor.newBuilder()
            descriptorBuilder.name = k
            val irAttribute = mappingCtx.irAttributeValueForNode(v)

            listOf(ArgDescriptor.ArgType.INT64, ArgDescriptor.ArgType.DOUBLE)
                .forEach { argDescriptorType ->
                    val targetIdx = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = argDescriptorType
                    )

                    if (targetIdx >= 0) {
                        when (argDescriptorType) {
                            ArgDescriptor.ArgType.DOUBLE -> {
                                descriptorBuilder.argType = argDescriptorType
                                descriptorBuilder.doubleValue = if (irAttribute.boolValue()) 1.0 else 0.0
                                descriptorBuilder.argIndex = targetIdx
                            }
                            ArgDescriptor.ArgType.INT64 -> {
                                descriptorBuilder.argType = argDescriptorType
                                descriptorBuilder.int64Value = if (irAttribute.boolValue()) 1 else 0
                                descriptorBuilder.argIndex = targetIdx
                            }

                            else -> {
                                throw IllegalArgumentException("Illegal type passed in $argDescriptorType")
                            }
                        }

                        ret.add(descriptorBuilder.build())

                    }


                }


        }
        return ret
    }
}


abstract class StringToInt<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "stringtoindex", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()

        for ((k, v) in mappingNamesToPerform()) {
            val listOfValues = (transformerArgs[k] ?: error("Unable to map value $v to a type string for op name ${mappingCtx.nd4jOpName()} and input op name ${mappingCtx.opName()}")).map { argDescriptor -> argDescriptor.stringValue }
            val stringValIndex = mappingCtx.irAttributeValueForNode(v).stringValue()
            val argDescriptor = ArgDescriptor {
                name = k
                argType = ArgDescriptor.ArgType.INT64
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INT64
                )
                int64Value = listOfValues.indexOf(stringValIndex).toLong()
            }

            ret.add(argDescriptor)

        }

        return ret
    }
}




abstract class MapStringToInt<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (name = "mapstringtoindex", mappingNamesToPerform = mappingNamesToPerform, transformerArgs = transformerArgs) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.LIST_STRING
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        val indexOfValue = transformerArgs["index"]!![0].int64Value
        for ((k, v) in mappingNamesToPerform()) {

            val stringVal = mappingCtx.irAttributeValueForNode(v).listStringValue()[indexOfValue.toInt()]
            val activationInt = (transformerArgs[k] ?: error("Unable to map value $v to a type string for op name ${mappingCtx.nd4jOpName()} and input op name ${mappingCtx.opName()}"))
                .filter {argDescriptor -> argDescriptor.name == stringVal }
                .map { argDescriptor -> argDescriptor.int64Value }.first()
            val argDescriptor = ArgDescriptor {
                name = k
                argType = ArgDescriptor.ArgType.INT64
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingCtx.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INT64
                )
                int64Value = activationInt
            }

            ret.add(argDescriptor)

        }

        return ret
    }
}


abstract class ListAttributeValueLookupToIndex<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "listattributevaluelookuptoindex",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.LIST_FLOAT ||
                argDescriptorType == AttributeValueType.LIST_INT ||
                argDescriptorType == AttributeValueType.LIST_STRING ||
                argDescriptorType == AttributeValueType.LIST_TENSOR ||
                argDescriptorType == AttributeValueType.LIST_BOOL ||
                argDescriptorType == AttributeValueType.INT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return !argDescriptorType.contains(ArgDescriptor.ArgType.OUTPUT_TENSOR)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val index = (transformerArgs[k] ?: error(""))[0]!!.int64Value
            val listOfValues = mappingCtx.irAttributeValueForNode(v)
            when (listOfValues.attributeValueType()) {
                AttributeValueType.LIST_FLOAT -> {
                    val listFloat = listOfValues.listFloatValue()
                    val argDescriptor = ArgDescriptor {
                        name = k
                        doubleValue = listFloat[index.toInt()].toDouble()
                        argType = ArgDescriptor.ArgType.DOUBLE
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.DOUBLE
                        )
                    }

                    ret.add(argDescriptor)
                }
                AttributeValueType.LIST_INT -> {
                    val listInt = listOfValues.listIntValue()
                    val argDescriptor = ArgDescriptor {
                        name = k
                        int64Value = listInt[index.toInt()]
                        argType = ArgDescriptor.ArgType.INT64
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INT64
                        )
                    }

                    ret.add(argDescriptor)
                }

                AttributeValueType.LIST_STRING -> {
                    val listString = listOfValues.listStringValue()
                    val argDescriptor = ArgDescriptor {
                        name = k
                        stringValue = listString[index.toInt()]
                        argType = ArgDescriptor.ArgType.STRING
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.STRING
                        )
                    }

                    ret.add(argDescriptor)
                }

                AttributeValueType.LIST_TENSOR -> {
                    val listTensor = listOfValues.listTensorValue()
                    val argDescriptor = ArgDescriptor {
                        name = k
                        inputValue = listTensor[index.toInt()].toArgTensor()
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INPUT_TENSOR
                        )
                    }

                    ret.add(argDescriptor)
                }

                AttributeValueType.LIST_BOOL -> {
                    val listBool = listOfValues.listBoolValue()
                    val argDescriptor = ArgDescriptor {
                        name = k
                        boolValue = listBool[index.toInt()]
                        argType = ArgDescriptor.ArgType.BOOL
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.BOOL
                        )
                    }

                    ret.add(argDescriptor)
                }

            }


        }

        return ret
    }
}


abstract class AttributeNumberListNDArray<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "convertinputnumberlisttondarray",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {


    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.LIST_FLOAT ||
                argDescriptorType == AttributeValueType.LIST_INT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INPUT_TENSOR)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val irAttribute = mappingCtx.irAttributeValueForNode(v)
            /**
             * TODO: add index mapping by doing a list lookup of what value
             * is present in the key. Each final arg descriptor can be used to
             * look up the intended index of the argument.
             */

            when (irAttribute.attributeValueType()) {
                AttributeValueType.LIST_FLOAT -> {
                    val listArr = irAttribute.listFloatValue().toFloatArray()
                    val ndarray = Nd4j.create(listArr)
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        name = k
                        inputValue = nameSpaceTensorFromNDarray(ndarray)
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.DOUBLE
                        )
                    })
                }

                AttributeValueType.LIST_INT -> {
                    val intArr = irAttribute.listIntValue().toLongArray()
                    val strides = Nd4j.getStrides(1, 4).toList().map { it.toLong() }.toLongArray()
                    val ndarray =
                        Nd4j.create(intArr, longArrayOf(1, intArr.size.toLong()), strides, 'c', DataType.INT64)
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        name = k
                        inputValue = nameSpaceTensorFromNDarray(ndarray)
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INT64
                        )
                    })
                }

            }

        }

        return ret
    }
}

abstract class ListNumberToListNumber<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "listnumbertolistnumber",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.INT ||
                argDescriptorType == AttributeValueType.FLOAT ||
                argDescriptorType == AttributeValueType.LIST_INT ||
                argDescriptorType == AttributeValueType.LIST_FLOAT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.DOUBLE)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {

            val irAttribute = mappingCtx.irAttributeValueForNode(v)
            when (irAttribute.attributeValueType()) {
                AttributeValueType.LIST_INT -> {
                    val baseIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.INT64
                    )
                    val listInts = irAttribute.listIntValue()
                    listInts.forEachIndexed { index, element ->
                        val finalName = if (index > 0) k + "$index" else k
                        val argDescriptor = ArgDescriptor {
                            name = finalName
                            int64Value = element
                            argType = ArgDescriptor.ArgType.INT64
                            argIndex = baseIndex + index
                        }

                        ret.add(argDescriptor)
                    }
                }
                AttributeValueType.LIST_FLOAT -> {
                    val baseIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = ArgDescriptor.ArgType.DOUBLE
                    )
                    val listFloats = irAttribute.listFloatValue()
                    listFloats.forEachIndexed { index, element ->
                        val finalName = if (index > 0) k + "$index" else k
                        val argDescriptor = ArgDescriptor {
                            name = finalName
                            doubleValue = element.toDouble()
                            argType = ArgDescriptor.ArgType.DOUBLE
                            argIndex = baseIndex + index
                        }

                        ret.add(argDescriptor)
                    }
                }
            }
        }

        return ret
    }
}


abstract class ListNumberToNDArray<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "listnumbertondarray",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.INT ||
                argDescriptorType == AttributeValueType.FLOAT ||
                argDescriptorType == AttributeValueType.LIST_INT ||
                argDescriptorType == AttributeValueType.LIST_FLOAT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INPUT_TENSOR)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val listOfValues = mappingCtx.irAttributeValueForNode(v)
            val baseIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = ArgDescriptor.ArgType.INPUT_TENSOR
            )

            when (listOfValues.attributeValueType()) {
                AttributeValueType.LIST_FLOAT -> {
                    val nd4jArray = Nd4j.create(listOfValues.listFloatValue().toFloatArray())
                    val inputTensor = nameSpaceTensorFromNDarray(nd4jArray)
                    ret.add(ArgDescriptor {
                        name = k
                        inputValue = inputTensor
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        argIndex = baseIndex
                    })
                }

                AttributeValueType.LIST_INT -> {
                    val nd4jArray = Nd4j.create(Nd4j.createBuffer(listOfValues.listIntValue().toLongArray()))
                    val inputTensor = nameSpaceTensorFromNDarray(nd4jArray)
                    ret.add(ArgDescriptor {
                        name = k
                        inputValue = inputTensor
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        argIndex = baseIndex
                    })
                }

            }

        }

        return ret
    }
}


abstract class NDArrayAttributeToNDArrayInput<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "ndarrayinputtondarray",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INPUT_TENSOR)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val baseIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = ArgDescriptor.ArgType.INPUT_TENSOR
            )
            val attr = mappingCtx.irAttributeValueForNode(v).tensorValue()
            ret.add(ArgDescriptor {
                name = k
                inputValue = attr.toArgTensor()
                argType = ArgDescriptor.ArgType.INPUT_TENSOR
                argIndex = baseIndex
            })

        }


        return ret
    }
}


abstract class NDArrayInputToNumericalAttribute<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "ndarrayinputtonumericalattribute",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.DOUBLE)
                || argDescriptorType.contains(ArgDescriptor.ArgType.INT64) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.FLOAT)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        val realDescriptor = nd4jOpDescriptors.findOp(mappingCtx.nd4jOpName())

        for ((k, v) in mappingNamesToPerform()) {
            val inputTensor = mappingCtx.tensorInputFor(v).toNd4jNDArray()
            realDescriptor.argDescriptorList.filter { argDescriptor -> argDescriptor.name == k &&
                    argDescriptor.argType == ArgDescriptor.ArgType.INT64 && argDescriptor.name == k || argDescriptor.argType == ArgDescriptor.ArgType.DOUBLE && argDescriptor.name == k}
                .forEach { argDescriptor ->
                    val baseIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = k,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = argDescriptor.argType
                    )
                    for (i in 0 until 1) {
                        val nameToUse = if (i > 0) k + "$i" else k
                        when (argDescriptor.argType) {
                            ArgDescriptor.ArgType.DOUBLE -> {
                                ret.add(ArgDescriptor {
                                    name = nameToUse
                                    argType = ArgDescriptor.ArgType.DOUBLE
                                    doubleValue = inputTensor.getDouble(i)
                                    argIndex = baseIndex + i.toInt()
                                })
                            }

                            ArgDescriptor.ArgType.INT64 -> {
                                ret.add(ArgDescriptor {
                                    name = nameToUse
                                    argType = ArgDescriptor.ArgType.INT64
                                    int64Value = inputTensor.getInt(i).toLong()
                                    argIndex = baseIndex + i.toInt()
                                })
                            }
                        }

                    }
                }

        }

        return ret
    }
}


abstract class AttributeNDArrayToScalarAttribute<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "attributendarraytoscalarattribute",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.DOUBLE) ||
                argDescriptorType.contains(ArgDescriptor.ArgType.INT32)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val irAttribute = mappingCtx.tensorAttributeFor(v).toNd4jNDArray()
            val realDataType = argDescriptorType(k, nd4jOpDescriptors.findOp(mappingCtx.nd4jOpName()))
            when(realDataType) {
                ArgDescriptor.ArgType.DOUBLE -> {
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.DOUBLE
                        name = k
                        doubleValue = irAttribute.getDouble(0)
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.DOUBLE
                        )
                    })
                }

                ArgDescriptor.ArgType.INT64 -> {
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.INT64
                        name = k
                        int64Value = irAttribute.getInt(0).toLong()
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INT64
                        )
                    })
                }
            }

        }

        return ret
    }
}


abstract class AttributeScalarNDArrayAttribute<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "attributescalarndarrayattribute",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.FLOAT || argDescriptorType == AttributeValueType.INT
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INPUT_TENSOR)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val irAttribute = mappingCtx.irAttributeValueForNode(v)
            when (irAttribute.attributeValueType()) {
                AttributeValueType.FLOAT -> {
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        name = k
                        inputValue = nameSpaceTensorFromNDarray(Nd4j.scalar(irAttribute.floatValue()).reshape(1, 1))
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INPUT_TENSOR
                        )
                    })
                }

                AttributeValueType.INT -> {
                    ret.add(ArgDescriptor {
                        argType = ArgDescriptor.ArgType.INPUT_TENSOR
                        name = k
                        inputValue = nameSpaceTensorFromNDarray(Nd4j.scalar(irAttribute.intValue()).reshape(1, 1))
                        argIndex = lookupIndexForArgDescriptor(
                            argDescriptorName = k,
                            opDescriptorName = mappingCtx.nd4jOpName(),
                            argDescriptorType = ArgDescriptor.ArgType.INT64
                        )
                    })
                }
                else -> {
                    throw IllegalArgumentException("Attribute $v is not a valid type. Type was ${irAttribute.attributeValueType()}")
                }

            }

        }

        return ret
    }
}


abstract class NDArrayToIntAttributeValue<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String>,
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "ndarraytointattributevalue",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {
    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return argDescriptorType == AttributeValueType.TENSOR
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return argDescriptorType.contains(ArgDescriptor.ArgType.INT64)
    }

    override fun convertAttributes(mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        for ((k, v) in mappingNamesToPerform()) {
            val ndarray = mappingCtx.tensorInputFor(v).toNd4jNDArray()
            val arrInts = ndarray.ravel().toIntVector()
            val baseIndex = lookupIndexForArgDescriptor(
                argDescriptorName = k,
                opDescriptorName = mappingCtx.nd4jOpName(),
                argDescriptorType = ArgDescriptor.ArgType.INT64
            )
            for (i in 0 until ndarray.length()) {
                val argDescriptor = ArgDescriptor {
                    name = k
                    int64Value = arrInts[i.toInt()].toLong()
                    argType = ArgDescriptor.ArgType.INT64
                    argIndex = (baseIndex + i).toInt()
                }

                ret.add(argDescriptor)
            }
        }

        return ret
    }
}


abstract class BaseNDArrayMappingRule<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3, NODE_DEF_TYPE : GeneratedMessageV3, ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3, TENSOR_TYPE : GeneratedMessageV3,
        DATA_TYPE>(
    mappingNamesToPerform: MutableMap<String, String> = mutableMapOf(),
    transformerArgs: Map<String, List<ArgDescriptor>> = emptyMap()
) :
    TensorMappingRule<GRAPH_DEF, OP_DEF_TYPE, NODE_DEF_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        where DATA_TYPE : ProtocolMessageEnum {

    protected var opDescriptor: OpDescriptor? = null
    protected val mappingNamesToPerform = mappingNamesToPerform
    protected val transformerArgs = transformerArgs
    protected var mappingProcess: MappingProcess<GRAPH_DEF, OP_DEF_TYPE, NODE_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>? =
        null


    override fun initWithMappingProcess(mappingProcess: MappingProcess<GRAPH_DEF, OP_DEF_TYPE, NODE_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>) {
        val opDescriptorList = nd4jOpDescriptors
        if (!opDescriptorList.opListList.map { it -> it.name }.contains(mappingProcess.opName())) {
            throw java.lang.IllegalArgumentException("Op name ${mappingProcess.opName()} not found!")
        }
        opDescriptor = opDescriptorList.opListList.first { input ->
            input.name == mappingProcess.opName()
        } ?: error("")
        this.mappingProcess = mappingProcess
    }


    operator fun set(outputAttribute: String, inputAttribute: String) {
        mappingNamesToPerform[outputAttribute] = inputAttribute
    }

    override fun name(): String {
        return "ndarraymapping"
    }


    override fun mappingNamesToPerform(): Map<String, String> {
        return mappingNamesToPerform
    }


    override fun convertInput(mappingContext: MappingContext<GRAPH_DEF, NODE_DEF_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, DATA_TYPE>): List<ArgDescriptor> {
        val ret = ArrayList<ArgDescriptor>()
        val mappingsToPerform = inputArgumentMappings()
        mappingsToPerform.forEach { (k, v) ->
            ret.add(ArgDescriptor {
                name = k
                argType = ArgDescriptor.ArgType.INPUT_TENSOR
                inputValue = mappingContext.tensorInputFor(v).toArgTensor()
                argIndex = lookupIndexForArgDescriptor(
                    argDescriptorName = k,
                    opDescriptorName = mappingContext.nd4jOpName(),
                    argDescriptorType = ArgDescriptor.ArgType.INPUT_TENSOR
                )
            })
        }


        return ret
    }

    abstract fun createTensorProto(input: TENSOR_TYPE): TensorNamespace.TensorProto


    override fun convertInputsReverse(toReverse: List<ArgDescriptor>): List<TENSOR_TYPE> {
        for (argument in toReverse) {
            require(argument.argType == ArgDescriptor.ArgType.INPUT_TENSOR) { "Type to reverse must be an input tensor." }
        }
        TODO("Not yet implemented")
    }

    override fun inputArgumentMappings(): Map<String, String> {
        return mappingNamesToPerform
    }

    override fun serialize(): MapperNamespace.MappingRule {
        val builder = MapperNamespace.MappingRule.newBuilder()
        builder.ruleName = name()
        builder.functionName = name()
        for ((k, v) in transformerArgs) {
            val descriptor = opDescriptor!!.argDescriptorList.filter { input -> input.name == k }[0]
            when (descriptor.argType) {
                ArgDescriptor.ArgType.BOOL -> builder.addOutputBooleanName(k)
                ArgDescriptor.ArgType.INT64 -> builder.addOutputIntName(k)
                ArgDescriptor.ArgType.FLOAT -> builder.addOutputFloatName(k)
                ArgDescriptor.ArgType.DOUBLE -> builder.addOutputDoubleName(k)
                ArgDescriptor.ArgType.INT64 -> builder.addOutputIntName(k)
                ArgDescriptor.ArgType.INPUT_TENSOR -> builder.addInputTensorName(k)
                ArgDescriptor.ArgType.OUTPUT_TENSOR -> builder.addOutputTensorName(k)
            }

            for (associatedInput in v) {
                when (associatedInput.argType) {
                    AttributeValueType.STRING -> builder.addInputStringAttrName(associatedInput.name)
                    AttributeValueType.BOOL -> builder.addInputBooleanName(associatedInput.name)
                    AttributeValueType.FLOAT -> builder.addInputFloatName(associatedInput.name)
                    AttributeValueType.INT -> builder.addInputIntName(associatedInput.name)
                    AttributeValueType.TENSOR -> builder.addInputTensorName(associatedInput.name)
                }
            }


        }


        return builder.build()
    }

}


abstract class ArgDescriptorConstant<
        GRAPH_DEF : GeneratedMessageV3,
        OP_DEF_TYPE : GeneratedMessageV3,
        NODE_TYPE : GeneratedMessageV3,
        ATTR_DEF : GeneratedMessageV3,
        ATTR_VALUE_TYPE : GeneratedMessageV3,
        TENSOR_TYPE : GeneratedMessageV3, DATA_TYPE : ProtocolMessageEnum>(
    mappingNamesToPerform: Map<String, String> = emptyMap(),
    transformerArgs: Map<String, List<ArgDescriptor>>
) :
    BaseAttributeExtractionRule<GRAPH_DEF, OP_DEF_TYPE, NODE_TYPE, ATTR_DEF, ATTR_VALUE_TYPE, TENSOR_TYPE, DATA_TYPE>
        (
        name = "argdescriptorconstant",
        mappingNamesToPerform = mappingNamesToPerform,
        transformerArgs = transformerArgs
    ) {

    override fun acceptsInputType(argDescriptorType: AttributeValueType): Boolean {
        return true
    }

    override fun outputsType(argDescriptorType: List<ArgDescriptor.ArgType>): Boolean {
        return true
    }

    override fun convertAttributes(
        mappingCtx: MappingContext<GRAPH_DEF, NODE_TYPE, OP_DEF_TYPE, TENSOR_TYPE, ATTR_DEF,
                ATTR_VALUE_TYPE, DATA_TYPE>
    ): List<ArgDescriptor> {
        return transformerArgs.flatMap {
            it.value.map { descriptor ->
                ArgDescriptor {
                    name = descriptor.name
                    argIndex = lookupIndexForArgDescriptor(
                        argDescriptorName = descriptor.name,
                        opDescriptorName = mappingCtx.nd4jOpName(),
                        argDescriptorType = descriptor.argType
                    )
                    argType = descriptor.argType
                    floatValue = descriptor.floatValue
                    doubleValue = descriptor.doubleValue
                    int32Value = descriptor.int32Value
                    int64Value = descriptor.int64Value
                    stringValue = descriptor.stringValue
                    inputValue = descriptor.inputValue
                    outputValue = descriptor.outputValue

                }
            }
        }
    }
}
