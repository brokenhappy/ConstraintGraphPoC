package constraintGraphBuilder

sealed interface PositionPrototype {
    var node: ExpressionalNodePrototype?
    fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator): Position
    class Argument(private val index: Int) : PositionPrototype {
        override var node: ExpressionalNodePrototype? = null
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) =
            Position.Argument(node, index)
    }

    class ClosureParameter(
        val name: String,
        private val argument: Argument,
        private val paramIndex: Int
    ) : PositionPrototype {
        override var node: ExpressionalNodePrototype? = null
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) = Position.ClosureParameter(
            name,
            entityHydrator.cached(argument, node),
            paramIndex,
        )
    }

    class ClosureImage(private val argument: Argument) : PositionPrototype {
        override var node: ExpressionalNodePrototype? = null
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) =
            Position.ClosureImage(entityHydrator.cached(argument, node))
    }

    class Image : PositionPrototype {
        override var node: ExpressionalNodePrototype? = null
        override fun manufacture(node: ExpressionalNode, entityHydrator: EntityHydrator) = Position.Image(node)
    }
}
