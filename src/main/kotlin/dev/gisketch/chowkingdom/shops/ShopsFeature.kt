package dev.gisketch.chowkingdom.shops

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ShopsFeature {
    private val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(Registries.BLOCK, ChowKingdomMod.MOD_ID)
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)

    private val woodShopBlocks = mutableListOf<DeferredHolder<Block, FacingShopBlock>>()
    private val simpleShopBlocks = mutableListOf<DeferredHolder<Block, out Block>>()

    private val WOOD_VARIANTS = listOf(
        "acacia",
        "bamboo",
        "birch",
        "cherry",
        "crimson",
        "dark_oak",
        "mangrove",
        "spruce",
        "warped",
        "jungle",
        "oak",
    )

    private val COLOURS = listOf(
        "red",
        "white",
        "blue",
        "purple",
        "green",
        "lime",
        "orange",
        "gray",
        "black",
        "light_gray",
        "brown",
        "yellow",
        "light_blue",
        "cyan",
        "magenta",
        "pink",
    )

    private val RUG_BLOCKS = listOf(
        "rug_shop",
        "rug_shop_white",
        "rug_shop_orange",
        "rug_shop_magenta",
        "rug_shop_light_blue",
        "rug_shop_yellow",
        "rug_shop_lime",
        "rug_shop_pink",
        "rug_shop_gray",
        "rug_shop_light_gray",
        "rug_shop_cyan",
        "rug_shop_purple",
        "rug_shop_blue",
        "rug_shop_brown",
        "rug_shop_green",
        "rug_shop_black",
    )

    init {
        WOOD_VARIANTS.forEach { variant ->
            woodShopBlocks += registerWoodShop("shop_$variant")
        }

        simpleShopBlocks += registerBlock("hook_shop") { FacingShopBlock(shopProperties(Blocks.CHAIN), HookShopBlock.SHAPE) }
        simpleShopBlocks += registerBlock("crate_shop") { FacingShopBlock(shopProperties(Blocks.OAK_PLANKS)) }
        simpleShopBlocks += registerBlock("shop_window_calcite") { FacingShopBlock(shopProperties(Blocks.STONE), WindowShopBlock.SHAPE_NORTH_SOUTH, WindowShopBlock.SHAPE_EAST_WEST) }
        simpleShopBlocks += registerBlock("shop_window_andesite") { FacingShopBlock(shopProperties(Blocks.STONE), WindowShopBlock.SHAPE_NORTH_SOUTH, WindowShopBlock.SHAPE_EAST_WEST) }

        RUG_BLOCKS.forEach { id ->
            simpleShopBlocks += registerBlock(id) { RugShopBlock(shopProperties(Blocks.RED_CARPET)) }
        }

        WOOD_VARIANTS.forEach { variant ->
            simpleShopBlocks += registerBlock("shelf_shop_$variant") { ShelfShopBlock(shopProperties(Blocks.OAK_PLANKS)) }
        }
    }

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
    }

    private fun registerWoodShop(id: String): DeferredHolder<Block, FacingShopBlock> {
        val block = registerBlock(id) { FacingShopBlock(shopProperties(Blocks.OAK_PLANKS)) }
        COLOURS.forEach { colour ->
            ITEMS.register("${id}_$colour", Supplier { BlockItem(block.get(), Item.Properties()) })
        }
        return block
    }

    private fun <T : Block> registerBlock(id: String, factory: () -> T): DeferredHolder<Block, T> {
        val block = BLOCKS.register(id, Supplier { factory() })
        ITEMS.register(id, Supplier { BlockItem(block.get(), Item.Properties()) })
        return block
    }

    private fun shopProperties(example: Block): BlockBehaviour.Properties =
        BlockBehaviour.Properties.ofFullCopy(example)
            .noOcclusion()
            .strength(2.0f, Float.MAX_VALUE)
}

private open class FacingShopBlock(
    properties: BlockBehaviour.Properties,
    private val northSouthShape: VoxelShape = Shapes.block(),
    private val eastWestShape: VoxelShape = northSouthShape,
) : Block(properties) {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun rotate(state: BlockState, level: LevelAccessor, pos: BlockPos, direction: Rotation): BlockState =
        state.setValue(FACING, direction.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)))

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (state.getValue(FACING)) {
            Direction.EAST, Direction.WEST -> eastWestShape
            else -> northSouthShape
        }

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}

private class RugShopBlock(properties: BlockBehaviour.Properties) : Block(properties) {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(NORTH, EAST, SOUTH, WEST)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val level = context.level
        val pos = context.clickedPos
        return defaultBlockState()
            .setValue(NORTH, level.getBlockState(pos.north()).block == this)
            .setValue(EAST, level.getBlockState(pos.east()).block == this)
            .setValue(SOUTH, level.getBlockState(pos.south()).block == this)
            .setValue(WEST, level.getBlockState(pos.west()).block == this)
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        val property = when (direction) {
            Direction.NORTH -> NORTH
            Direction.EAST -> EAST
            Direction.SOUTH -> SOUTH
            Direction.WEST -> WEST
            else -> return state
        }
        return state.setValue(property, neighborState.block == this)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    companion object {
        val NORTH: BooleanProperty = BooleanProperty.create("north")
        val EAST: BooleanProperty = BooleanProperty.create("east")
        val SOUTH: BooleanProperty = BooleanProperty.create("south")
        val WEST: BooleanProperty = BooleanProperty.create("west")
        val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
    }
}

private class ShelfShopBlock(properties: BlockBehaviour.Properties) : FacingShopBlock(properties) {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, SlabType.DOUBLE),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, TYPE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState()
            .setValue(FACING, context.horizontalDirection.opposite)
            .setValue(TYPE, if (context.clickLocation.y - context.clickedPos.y > 0.5) SlabType.TOP else SlabType.BOTTOM)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val facing = state.getValue(FACING)
        val index = when (facing) {
            Direction.WEST -> 1
            Direction.SOUTH -> 2
            Direction.EAST -> 3
            else -> 0
        }
        return when (state.getValue(TYPE)) {
            SlabType.TOP -> TOP_SHAPES[index]
            SlabType.BOTTOM -> BOTTOM_SHAPES[index]
            else -> DOUBLE_SHAPES[index]
        }
    }

    companion object {
        val TYPE = BlockStateProperties.SLAB_TYPE
        private val TOP_SHAPES = arrayOf(
            box(0.5, 8.0, 8.0, 15.5, 14.0, 16.0),
            box(8.0, 8.0, 0.5, 16.0, 14.0, 15.5),
            box(0.5, 8.0, 0.0, 15.5, 14.0, 8.0),
            box(0.0, 8.0, 0.5, 8.0, 14.0, 15.5),
        )
        private val BOTTOM_SHAPES = arrayOf(
            box(0.5, 0.0, 8.0, 15.5, 6.0, 16.0),
            box(8.0, 0.0, 0.5, 16.0, 6.0, 15.5),
            box(0.5, 0.0, 0.0, 15.5, 6.0, 8.0),
            box(0.0, 0.0, 0.5, 8.0, 6.0, 15.5),
        )
        private val DOUBLE_SHAPES = Array(4) { index -> Shapes.or(TOP_SHAPES[index], BOTTOM_SHAPES[index]) }
    }
}

private object HookShopBlock {
    val SHAPE: VoxelShape = Block.box(5.0, -1.0, 5.0, 11.0, 16.0, 11.0)
}

private object WindowShopBlock {
    val SHAPE_NORTH_SOUTH: VoxelShape = Block.box(0.0, -1.0, -1.0, 16.0, 2.0, 17.0)
    val SHAPE_EAST_WEST: VoxelShape = Block.box(-1.0, -1.0, 0.0, 17.0, 2.0, 16.0)
}
