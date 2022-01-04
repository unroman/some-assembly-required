package someassemblyrequired.common.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.registries.ForgeRegistries;
import someassemblyrequired.SomeAssemblyRequired;

import javax.annotation.Nullable;

public record DataIngredient(
        @Nullable FoodProperties foodProperties,
        @Nullable Component displayName,
        @Nullable Component fullName,
        ItemStack displayItem,
        ItemStack container,
        @Nullable SoundEvent soundEvent
) implements SandwichIngredient {

    @Override
    @Nullable
    public FoodProperties getFood(ItemStack item) {
        if (foodProperties == null) {
            return SandwichIngredient.super.getFood(item);
        }
        return foodProperties;
    }

    @Override
    public Component getDisplayName(ItemStack item) {
        if (displayName == null) {
            return SandwichIngredient.super.getDisplayName(item);
        }
        return displayName;
    }

    @Override
    public Component getFullName(ItemStack item) {
        if (fullName == null) {
            return SandwichIngredient.super.getFullName(item);
        }
        return fullName;
    }

    @Override
    public ItemStack getDisplayItem(ItemStack item) {
        if (displayItem.isEmpty()) {
            return SandwichIngredient.super.getDisplayItem(item);
        }
        return displayItem;
    }

    @Override
    public ItemStack getContainer(ItemStack item) {
        if (container.isEmpty()) {
            return SandwichIngredient.super.getContainer(item);
        }
        return container;
    }

    @Override
    public void playApplySound(ItemStack item, Level level, @Nullable Player player, BlockPos pos) {
        if (soundEvent != null) {
            level.playSound(player, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.3F, 1.3F);
        } else {
            SandwichIngredient.super.playRemoveSound(item, level, player, pos);
        }
    }

    @Override
    public void playRemoveSound(ItemStack item, Level level, Player player, BlockPos pos) {
        if (soundEvent != null) {
            level.playSound(player, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.3F, 1.6F);
        } else {
            SandwichIngredient.super.playRemoveSound(item, level, player, pos);
        }
    }

    public JsonObject toJson(Item item) {
        JsonObject result = new JsonObject();
        // noinspection ConstantConditions
        result.addProperty("item", item.getRegistryName().toString());
        if (foodProperties != null) {
            result.add("food", writeFoodProperties(foodProperties));
        }
        if (displayName != null) {
            result.add("displayName", Component.Serializer.toJsonTree(displayName));
        }
        if (fullName != null) {
            result.add("fullName", Component.Serializer.toJsonTree(fullName));
        }
        if (!displayItem.isEmpty()) {
            result.add("displayItem", writeItemStack(displayItem));
        }
        if (!container.isEmpty()) {
            result.add("container", writeItemStack(container));
        }
        if (soundEvent != null) {
            // noinspection ConstantConditions
            result.addProperty("soundEvent", soundEvent.getRegistryName().toString());
        }

        return result;
    }

    public static DataIngredient fromJson(JsonObject object) {
        FoodProperties foodProperties = readFoodProperties(object, "food");
        Component displayName = null;
        if (object.has("displayName")) {
            displayName = Component.Serializer.fromJson(object.get("displayName"));
        }
        Component fullName = null;
        if (object.has("fullName")) {
            fullName = Component.Serializer.fromJson(object.get("fullName"));
        }
        ItemStack displayItem = readOptionalItemStack(object, "displayItem");
        ItemStack container = readOptionalItemStack(object, "container");
        SoundEvent soundEvent = null;
        if (object.has("soundEvent")) {
            ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(object, "soundEvent"));
            if (!ForgeRegistries.SOUND_EVENTS.containsKey(id)) {
                throw new JsonParseException("No such sound event: " + id);
            }
            soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(id);
        }
        return new DataIngredient(foodProperties, displayName, fullName, displayItem, container, soundEvent);
    }

    private static JsonElement writeFoodProperties(FoodProperties properties) {
        JsonObject result = new JsonObject();
        result.addProperty("nutrition", properties.getNutrition());
        result.addProperty("saturationModifier", properties.getSaturationModifier());
        if (properties.canAlwaysEat()) {
            result.addProperty("canAlwaysEat", properties.canAlwaysEat());
        }
        return result;
    }

    @Nullable
    private static FoodProperties readFoodProperties(JsonObject object, String memberName) {
        if (!object.has(memberName)) {
            return null;
        }
        JsonObject foodObject = GsonHelper.getAsJsonObject(object, memberName);
        FoodProperties.Builder builder = new FoodProperties.Builder();
        if (GsonHelper.getAsBoolean(foodObject, "canAlwaysEat")) {
            builder.alwaysEat();
        }
        return builder
                .nutrition(GsonHelper.getAsInt(foodObject, "nutrition"))
                .saturationMod(GsonHelper.getAsInt(foodObject, "saturationModifier"))
                .build();
    }

    private static JsonObject writeItemStack(ItemStack stack) {
        JsonObject object = new JsonObject();

        // noinspection ConstantConditions
        object.addProperty("item", stack.getItem().getRegistryName().toString());
        if (stack.hasTag()) {
            object.add("nbt", writeNbt(stack.getTag()));
        }

        return object;
    }

    private static ItemStack readOptionalItemStack(JsonObject object, String memberName) {
        if (!object.has(memberName)) {
            return ItemStack.EMPTY;
        }
        return CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(object, memberName), true);
    }

    private static JsonElement writeNbt(CompoundTag tag) {
        return CompoundTag.CODEC
                .encodeStart(JsonOps.INSTANCE, tag)
                .resultOrPartial(SomeAssemblyRequired.LOGGER::error)
                .orElseThrow();
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeBoolean(foodProperties != null);
        if (foodProperties != null) {
            writeFoodProperties(buffer, foodProperties);
        }
        buffer.writeBoolean(displayName != null);
        if (displayName != null) {
            buffer.writeComponent(displayName);
        }
        buffer.writeBoolean(fullName != null);
        if (fullName != null) {
            buffer.writeComponent(fullName);
        }
        buffer.writeItem(displayItem);
        buffer.writeItem(container);
        buffer.writeBoolean(soundEvent != null);
        if (soundEvent != null) {
            // noinspection ConstantConditions
            buffer.writeResourceLocation(soundEvent.getRegistryName());
        }
    }

    public static DataIngredient fromNetwork(FriendlyByteBuf buffer) {
        FoodProperties foodProperties = null;
        if (buffer.readBoolean()) {
            foodProperties = readFoodProperties(buffer);
        }
        Component displayName = null;
        if (buffer.readBoolean()) {
            displayName = buffer.readComponent();
        }
        Component fullName = null;
        if (buffer.readBoolean()) {
            fullName = buffer.readComponent();
        }
        ItemStack displayItem = buffer.readItem();
        ItemStack container = buffer.readItem();
        SoundEvent soundEvent = null;
        if (buffer.readBoolean()) {
            soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(buffer.readResourceLocation());
        }
        return new DataIngredient(foodProperties, displayName, fullName, displayItem, container, soundEvent);
    }

    private static void writeFoodProperties(FriendlyByteBuf buffer, FoodProperties foodProperties) {
        buffer.writeInt(foodProperties.getNutrition());
        buffer.writeFloat(foodProperties.getSaturationModifier());
        buffer.writeBoolean(foodProperties.canAlwaysEat());
    }

    private static FoodProperties readFoodProperties(FriendlyByteBuf buffer) {
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(buffer.readInt())
                .saturationMod(buffer.readFloat());
        if (buffer.readBoolean()) {
            builder.alwaysEat();
        }
        return builder.build();
    }
}
