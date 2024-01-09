package vct.test.integration.examples

import vct.test.integration.helper.VercorsSpec

class SYCLSpec extends VercorsSpec {
  vercors should verify using silicon example "concepts/sycl/kernels/BasicKernel.cpp"
  vercors should verify using silicon example "concepts/sycl/kernels/LocalVariableUsageInKernelRange.cpp"
  vercors should error withCode "syclNoMultipleKernels" example "concepts/sycl/kernels/MultipleKernelsInCommandGroup.cpp"
  vercors should verify using silicon example "concepts/sycl/kernels/NDRangeKernel.cpp"
  vercors should error withCode "syclItemMethodSeqBoundNegative" example "concepts/sycl/kernels/NegativeKernelDimensionForItemMethod.cpp"
  vercors should error withCode "syclKernelRangeInvalid" example "concepts/sycl/kernels/NegativeRange.cpp"
  vercors should error withCode "syclMissingKernel" example "concepts/sycl/kernels/NoKernelInCommandGroup.cpp"
  vercors should error withCode "syclKernelRangeInvalid" example "concepts/sycl/kernels/NonDivisibleNDRange.cpp"
  vercors should error withCode "syclIncorrectParallelForLambdaArgument" example "concepts/sycl/kernels/NonMatchingRangeItem.cpp"
  vercors should error withCode "syclIncorrectParallelForLambdaArgument" example "concepts/sycl/kernels/NonMatchingRangeItemDimensions.cpp"
  vercors should error withCode "syclNoExtraCodeInCommandGroup" example "concepts/sycl/kernels/NonSYCLCodeInCommandGroup.cpp"
  vercors should error withCode "syclNoExtraCodeInCommandGroup" example "concepts/sycl/kernels/OtherSYCLCodeInCommandGroup.cpp"
  vercors should error withCode "syclItemMethodSeqBoundExceedsLength" example "concepts/sycl/kernels/TooHighKernelDimensionForItemMethod.cpp"
  vercors should error withCode "resolutionError:outOfScope" example "concepts/sycl/kernels/UnsupportedLocalVariableUsageInCommandGroup.cpp"
  vercors should error withCode "syclKernelRangeInvalid" example "concepts/sycl/kernels/ZeroNDRange.cpp"

  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGlobalId.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGlobalLinearId1.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGlobalLinearId2.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGlobalLinearId3.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGroupLinearId1.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGroupLinearId2.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetGroupLinearId3.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetLinearId.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetId.cpp"
  vercors should verify using silicon example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetLocalLinearId1.cpp"
  vercors should verify using silicon example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetLocalLinearId2.cpp"
  vercors should verify using silicon example "concepts/sycl/kernels/itemMethodsInjective/injectiveGetLocalLinearId3.cpp"

  vercors should verify using silicon example "concepts/sycl/buffers/BufferDeclarations.cpp"
  vercors should error withCode "syclBufferConstructionFailed" example "concepts/sycl/buffers/NegativeRange.cpp"
  vercors should error withCode "syclUnsupportedReassigningOfBuffer" example "concepts/sycl/buffers/NoBufferReassign.cpp"
  vercors should error withCode "syclBufferConstructionFailed" example "concepts/sycl/buffers/NoWritePermission.cpp"
  vercors should fail withCode "ptrPerm" using silicon example "concepts/sycl/buffers/ReadDataInBufferScope.cpp"
  vercors should verify using silicon example "concepts/sycl/buffers/ReleaseDataFromBuffer.cpp"
  vercors should verify using silicon example "concepts/sycl/buffers/SmallerBuffer.cpp"
  vercors should error withCode "syclBufferConstructionFailed" example "concepts/sycl/buffers/TooBigBuffer.cpp"
  vercors should error withCode "syclBufferConstructionFailed" example "concepts/sycl/buffers/TwoBuffersForSameData.cpp"
  vercors should error withCode "noSuchName" example "concepts/sycl/buffers/UnfoldingExclusiveHostDataAccessPredicate.cpp"
  vercors should fail withCode "assignFieldFailed" using silicon example "concepts/sycl/buffers/WriteDataInBufferScope.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/buffers/WrongGenericArgumentForConstructorHostdataType.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/buffers/WrongGenericArgumentForConstructorRangeType1.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/buffers/WrongGenericArgumentForConstructorRangeType2.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/buffers/WrongGenericArgumentForHostdataType.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/buffers/WrongGenericArgumentForRangeType.cpp"

  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/dataAccessors/AccessorDeclarations.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/dataAccessors/AccessorInNDRange.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/dataAccessors/GetKernelResult.cpp"
  vercors should error withCode "syclAccessorInsufficientReferencePermission" example "concepts/sycl/dataAccessors/GetRangeDimensionOutOfBounds1.cpp"
  vercors should error withCode "syclAccessorInsufficientReferencePermission" example "concepts/sycl/dataAccessors/GetRangeDimensionOutOfBounds2.cpp"
  vercors should error withCode "syclAccessorArraySubscriptLinearizePreconditionFailed" example "concepts/sycl/dataAccessors/MissingRangeRequirements.cpp"
  vercors should error withCode "syclBufferOutOfScope" example  "concepts/sycl/dataAccessors/PassBufferToMethod.cpp"
  vercors should error withCode "syclAccessorArraySubscriptArrayBounds" example "concepts/sycl/dataAccessors/SubscriptOutOfBounds1.cpp"
  vercors should error withCode "syclAccessorArraySubscriptArrayBounds" example "concepts/sycl/dataAccessors/SubscriptOutOfBounds2.cpp"
  vercors should verify using silicon example "concepts/sycl/dataAccessors/TwoReadKernels.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/dataAccessors/TwoWriteAccessorsForSameBuffer.cpp"
  vercors should verify using silicon example "concepts/sycl/dataAccessors/TwoWriteKernels.cpp"
  vercors should verify using silicon example "concepts/sycl/dataAccessors/TwoWriteKernelsWithWait.cpp"
  vercors should verify using silicon flag "--no-infer-heap-context-into-frame" example "concepts/sycl/dataAccessors/WriteOnReadAccessorWithDoubleAccessors.cpp"
  vercors should error withCode "syclKernelForkPre" example "concepts/sycl/dataAccessors/WriteToReadAccessor.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/dataAccessors/WrongGenericArgumentForAccessType.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/dataAccessors/WrongGenericArgumentForBufferDataType.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/dataAccessors/WrongGenericArgumentForBufferRangeType.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/dataAccessors/WrongGenericArgumentForConstructorAccessType1.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/dataAccessors/WrongGenericArgumentForConstructorAccessType2.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/dataAccessors/WrongGenericArgumentForConstructorBufferDataType.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/dataAccessors/WrongGenericArgumentForConstructorBufferRangeType1.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/dataAccessors/WrongGenericArgumentForConstructorBufferRangeType2.cpp"

  vercors should error withCode "syclAccessorInsufficientReferencePermission" example "concepts/sycl/localAccessors/GetRangeDimensionOutOfBounds1.cpp"
  vercors should error withCode "syclAccessorInsufficientReferencePermission" example "concepts/sycl/localAccessors/GetRangeDimensionOutOfBounds2.cpp"
  vercors should verify using silicon example "concepts/sycl/localAccessors/LocalAccessorDeclarations.cpp"
  vercors should verify using silicon example "concepts/sycl/localAccessors/LocalAccessorUsage.cpp"
  vercors should error withCode "syclNoLocalAccessorsInBasicKernel" example "concepts/sycl/localAccessors/LocalAccessorUsageInBasicKernel.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/localAccessors/MissingGenericDatatypeArgumentForConstructor.cpp"
  vercors should fail withCode "arrayBounds" using silicon example "concepts/sycl/localAccessors/SubscriptOutOfBounds1.cpp"
  vercors should fail withCode "arrayBounds" using silicon example "concepts/sycl/localAccessors/SubscriptOutOfBounds2.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/localAccessors/WrongGenericArgumentForConstructorRangeType1.cpp"
  vercors should error withCode "notApplicable" example "concepts/sycl/localAccessors/WrongGenericArgumentForConstructorRangeType2.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/localAccessors/WrongGenericArgumentForDataType.cpp"
  vercors should error withCode "unexpectedCPPTypeError" example "concepts/sycl/localAccessors/WrongGenericArgumentForRangeType.cpp"
}