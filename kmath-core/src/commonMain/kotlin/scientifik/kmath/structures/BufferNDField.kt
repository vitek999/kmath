package scientifik.kmath.structures

import scientifik.kmath.operations.Field
import scientifik.kmath.operations.FieldElement

abstract class StridedNDField<T, F : Field<T>>(shape: IntArray, elementField: F) :
    AbstractNDField<T, F, NDBuffer<T>>(shape, elementField) {
    val strides = DefaultStrides(shape)

    abstract fun buildBuffer(size: Int, initializer: (Int) -> T): Buffer<T>

    /**
     * Convert any [NDStructure] to buffered structure using strides from this context.
     * If the structure is already [NDBuffer], conversion is free. If not, it could be expensive because iteration over indexes
     *
     * If the argument is [NDBuffer] with different strides structure, the new element will be produced.
     */
    fun NDStructure<T>.toBuffer(): NDBuffer<T> {
        return if (this is NDBuffer<T> && this.strides == this@StridedNDField.strides) {
            this
        } else {
            produce { index -> get(index) }
        }
    }
}


class BufferNDField<T, F : Field<T>>(
    shape: IntArray,
    elementField: F,
    val bufferFactory: BufferFactory<T>
) : StridedNDField<T, F>(shape, elementField) {

    override fun buildBuffer(size: Int, initializer: (Int) -> T): Buffer<T> = bufferFactory(size, initializer)

    override fun check(vararg elements: NDBuffer<T>) {
        if (!elements.all { it.strides == this.strides }) error("Element strides are not the same as context strides")
    }

    override val zero by lazy { produce { zero } }
    override val one by lazy { produce { one } }

    override fun produce(initializer: F.(IntArray) -> T): BufferNDElement<T, F> =
        BufferNDElement(
            this,
            buildBuffer(strides.linearSize) { offset -> elementField.initializer(strides.index(offset)) })

    override fun map(arg: NDBuffer<T>, transform: F.(T) -> T): BufferNDElement<T, F> {
        check(arg)
        return BufferNDElement(
            this,
            buildBuffer(arg.strides.linearSize) { offset -> elementField.transform(arg.buffer[offset]) })
    }

    override fun mapIndexed(
        arg: NDBuffer<T>,
        transform: F.(index: IntArray, T) -> T
    ): BufferNDElement<T, F> {
        check(arg)
        return BufferNDElement(
            this,
            buildBuffer(arg.strides.linearSize) { offset ->
                elementField.transform(
                    arg.strides.index(offset),
                    arg.buffer[offset]
                )
            })
    }

    override fun combine(
        a: NDBuffer<T>,
        b: NDBuffer<T>,
        transform: F.(T, T) -> T
    ): BufferNDElement<T, F> {
        check(a, b)
        return BufferNDElement(
            this,
            buildBuffer(strides.linearSize) { offset -> elementField.transform(a.buffer[offset], b.buffer[offset]) })
    }
}

class BufferNDElement<T, F : Field<T>>(override val context: StridedNDField<T, F>, override val buffer: Buffer<T>) :
    NDBuffer<T>,
    FieldElement<NDBuffer<T>, BufferNDElement<T, F>, StridedNDField<T, F>>,
    NDElement<T, F> {

    override val elementField: F
        get() = context.elementField

    override fun unwrap(): NDBuffer<T> =
        this

    override fun NDBuffer<T>.wrap(): BufferNDElement<T, F> =
        BufferNDElement(context, this.buffer)

    override val strides
        get() = context.strides

    override val shape: IntArray
        get() = context.shape

    override fun get(index: IntArray): T =
        buffer[strides.offset(index)]

    override fun elements(): Sequence<Pair<IntArray, T>> =
        strides.indices().map { it to get(it) }

    override fun map(action: F.(T) -> T) =
        context.run { map(this@BufferNDElement, action) }.wrap()

    override fun mapIndexed(transform: F.(index: IntArray, T) -> T) =
        context.run { mapIndexed(this@BufferNDElement, transform) }.wrap()
}

/**
 * Element by element application of any operation on elements to the whole array. Just like in numpy
 */
operator fun <T : Any, F : Field<T>> Function1<T, T>.invoke(ndElement: BufferNDElement<T, F>) =
    ndElement.context.run { ndElement.map { invoke(it) } }

/* plus and minus */

/**
 * Summation operation for [BufferNDElement] and single element
 */
operator fun <T : Any, F : Field<T>> BufferNDElement<T, F>.plus(arg: T) =
    context.run { map { it + arg } }

/**
 * Subtraction operation between [BufferNDElement] and single element
 */
operator fun <T : Any, F : Field<T>> BufferNDElement<T, F>.minus(arg: T) =
    context.run { map { it - arg } }

/* prod and div */

/**
 * Product operation for [BufferNDElement] and single element
 */
operator fun <T : Any, F : Field<T>> BufferNDElement<T, F>.times(arg: T) =
    context.run { map { it * arg } }

/**
 * Division operation between [BufferNDElement] and single element
 */
operator fun <T : Any, F : Field<T>> BufferNDElement<T, F>.div(arg: T) =
    context.run { map { it / arg } }