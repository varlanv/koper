package com.varlanv.koper.test

import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

object SerdeSamples {
    @De
    @Ser
    object EmptyObject

    @De
    @Ser
    data object EmptyDataObject

    @De
    @Ser
    class EmptyClass

    @De
    @Ser
    class ClassSingleInt(val int: Int)

    @De
    @Ser
    class DataClassSingleInt(val int: Int)

    @De
    @Ser
    class ClassSingleIntPrivateConstructorPublicInvoke private constructor(val int: Int) {
        companion object {
            operator fun invoke(int: Int) = ClassSingleIntPrivateConstructorPublicInvoke(int)
        }
    }

    @De
    @Ser
    class DataClassSingleIntPrivateConstructorPublicInvoke private constructor(val int: Int) {
        companion object {
            operator fun invoke(int: Int) = DataClassSingleIntPrivateConstructorPublicInvoke(int)
        }
    }

    @De
    @Ser
    class ClassSingleIntPrivateConstructorInternalInvoke private constructor(val int: Int) {
        companion object {
            internal operator fun invoke(int: Int) = ClassSingleIntPrivateConstructorPublicInvoke(int)
        }
    }

    @De
    @Ser
    class ClassSingleIntPrivateConstructorPrivateInvoke private constructor(val int: Int) {
        companion object {
            private operator fun invoke(int: Int) = ClassSingleIntPrivateConstructorPrivateInvoke(int)
        }
    }
}
