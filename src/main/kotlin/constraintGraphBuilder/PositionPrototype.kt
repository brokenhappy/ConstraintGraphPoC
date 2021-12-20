package constraintGraphBuilder

sealed interface PositionPrototype {
    fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator): Position
    class Argument(private val index: Int) : PositionPrototype {
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) =
            Position.Argument(node, index)
    }

    class ClosureParameter(
        val name: String,
        private val argument: Argument,
        private val paramIndex: Int
    ) : PositionPrototype {
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) = Position.ClosureParameter(
            name,
            entityHydrator.cached(argument, node),
            paramIndex,
        )
    }

    class ClosureImage(private val argument: Argument) : PositionPrototype {
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) =
            Position.ClosureImage(entityHydrator.cached(argument, node))
    }

    class Image : PositionPrototype {
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) = Position.Image(node)
        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun hashCode(): Int {
            return System.identityHashCode(this)
        }
    }
}
