package dev.gisketch.chowkingdom.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gisketch.chowkingdom.ChowKingdomMod;
import dev.gisketch.chowkingdom.npc.ChowNpcEntity;
import dev.gisketch.chowkingdom.npc.NpcTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class XaeroNpcMapCompat {
    private static final String SYSTEM_ID = "ckdm_npcs";
    private static final int WORLD_HEAD_SIZE = 30;
    private static final int MINIMAP_HEAD_SIZE = 10;

    private static volatile boolean registeredHandler;
    private static volatile boolean worldRegistered;
    private static volatile boolean minimapRegistered;
    private static volatile Object worldSystem;
    private static volatile Object minimapSystem;

    private XaeroNpcMapCompat() {
    }

    public static void register() {
        if (registeredHandler) {
            return;
        }
        registeredHandler = true;
        NeoForge.EVENT_BUS.addListener(XaeroNpcMapCompat::onClientTick);
    }

    public static boolean isCkdmNpcElement(Object element) {
        return trackedNpc(element) != null;
    }

    public static boolean renderWorldMapNpc(Object element, GuiGraphics guiGraphics, double x, double y) {
        TrackedNpc npc = trackedNpc(element);
        if (npc == null) {
            return false;
        }
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0D);
        renderCenteredHead(guiGraphics, npc.texture, WORLD_HEAD_SIZE);
        pose.popPose();
        return true;
    }

    public static boolean renderMinimapNpc(Object element, Object renderInfo, GuiGraphics guiGraphics, double depth) {
        TrackedNpc npc = trackedNpc(element);
        if (npc == null || isMinimapInWorldRender(renderInfo)) {
            return false;
        }
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0D, 0.0D, depth + 0.01D);
        renderCenteredHead(guiGraphics, npc.texture, MINIMAP_HEAD_SIZE);
        pose.popPose();
        return true;
    }

    private static boolean isMinimapInWorldRender(Object renderInfo) {
        Object location = field(renderInfo, "location");
        return location != null && "IN_WORLD".equals(String.valueOf(location));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (!worldRegistered) {
            worldRegistered = tryRegisterWorldMap();
        }
        if (!minimapRegistered) {
            minimapRegistered = tryRegisterMinimap();
        }
    }

    private static boolean tryRegisterWorldMap() {
        try {
            Class<?> systemInterface = Class.forName("xaero.map.radar.tracker.system.IPlayerTrackerSystem");
            Class<?> readerInterface = Class.forName("xaero.map.radar.tracker.system.ITrackedPlayerReader");
            Class<?> worldMapClass = Class.forName("xaero.map.WorldMap");
            Field managerField = worldMapClass.getField("playerTrackerSystemManager");
            Object manager = managerField.get(null);
            if (manager == null) {
                return false;
            }
            Object reader = readerProxy(readerInterface);
            Object system = systemProxy(systemInterface, reader);
            Method register = manager.getClass().getMethod("register", String.class, systemInterface);
            register.invoke(manager, SYSTEM_ID, system);
            worldSystem = system;
            ChowKingdomMod.Companion.getLOGGER().info("Registered CKDM NPC heads with Xaero World Map.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChowKingdomMod.Companion.getLOGGER().warn("Failed to register CKDM NPC heads with Xaero World Map.", exception);
            return false;
        }
    }

    private static boolean tryRegisterMinimap() {
        try {
            Class<?> systemInterface = Class.forName("xaero.common.minimap.radar.tracker.system.IPlayerTrackerSystem");
            Class<?> readerInterface = Class.forName("xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader");
            Class<?> hudModClass = Class.forName("xaero.common.HudMod");
            Object instance = hudModClass.getField("INSTANCE").get(null);
            if (instance == null) {
                return false;
            }
            Object manager = instance.getClass().getMethod("getPlayerTrackerSystemManager").invoke(instance);
            if (manager == null) {
                return false;
            }
            Object reader = readerProxy(readerInterface);
            Object system = systemProxy(systemInterface, reader);
            Method register = manager.getClass().getMethod("register", String.class, systemInterface);
            register.invoke(manager, SYSTEM_ID, system);
            minimapSystem = system;
            ChowKingdomMod.Companion.getLOGGER().info("Registered CKDM NPC heads with Xaero Minimap.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChowKingdomMod.Companion.getLOGGER().warn("Failed to register CKDM NPC heads with Xaero Minimap.", exception);
            return false;
        }
    }

    private static Object systemProxy(Class<?> systemInterface, Object reader) {
        return Proxy.newProxyInstance(
            systemInterface.getClassLoader(),
            new Class<?>[] { systemInterface },
            (proxy, method, args) -> {
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, args, "CkdmNpcXaeroTracker");
                }
                if ("getReader".equals(name)) {
                    return reader;
                }
                if ("getTrackedPlayerIterator".equals(name)) {
                    return trackedNpcs().iterator();
                }
                return null;
            }
        );
    }

    private static Object readerProxy(Class<?> readerInterface) {
        return Proxy.newProxyInstance(
            readerInterface.getClassLoader(),
            new Class<?>[] { readerInterface },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, args, "CkdmNpcXaeroReader");
                }
                TrackedNpc npc = args != null && args.length > 0 && args[0] instanceof TrackedNpc tracked ? tracked : null;
                if (npc == null) {
                    return null;
                }
                return switch (method.getName()) {
                    case "getId" -> npc.id;
                    case "getX" -> npc.x;
                    case "getY" -> npc.y;
                    case "getZ" -> npc.z;
                    case "getDimension" -> npc.dimension;
                    default -> null;
                };
            }
        );
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, String label) {
        return switch (method.getName()) {
            case "toString" -> label;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> args != null && args.length == 1 && proxy == args[0];
            default -> null;
        };
    }

    private static List<TrackedNpc> trackedNpcs() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return Collections.emptyList();
        }

        List<TrackedNpc> result = new ArrayList<>();
        Iterator<Entity> entities = level.entitiesForRendering().iterator();
        while (entities.hasNext()) {
            Entity entity = entities.next();
            if (!(entity instanceof ChowNpcEntity npc) || !npc.isAlive() || npc.isRemoved() || npc.isInvisible()) {
                continue;
            }
            String npcId = npc.getNpcId();
            result.add(new TrackedNpc(
                npc.getUUID(),
                npcId,
                npc.getX(),
                npc.getY(),
                npc.getZ(),
                npc.level().dimension(),
                NpcTextures.texture(npcId)
            ));
        }
        return result;
    }

    private static TrackedNpc trackedNpc(Object element) {
        if (element instanceof TrackedNpc npc) {
            return npc;
        }
        Object player = call(element, "getPlayer");
        if (player instanceof TrackedNpc npc) {
            return npc;
        }
        Object system = call(element, "getSystem");
        if (system != null && (system == worldSystem || system == minimapSystem)) {
            Object id = call(element, "getPlayerId");
            ResourceLocation fallbackTexture = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
            if (id instanceof UUID uuid) {
                return new TrackedNpc(uuid, "", 0.0D, 0.0D, 0.0D, Level.OVERWORLD, fallbackTexture);
            }
        }
        return null;
    }

    private static Object call(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object field(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void renderCenteredHead(GuiGraphics guiGraphics, ResourceLocation texture, int size) {
        int x = -size / 2;
        int y = -size / 2;
        RenderSystem.enableBlend();
        guiGraphics.blit(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        guiGraphics.blit(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    public record TrackedNpc(
        UUID id,
        String npcId,
        double x,
        double y,
        double z,
        ResourceKey<Level> dimension,
        ResourceLocation texture
    ) {
        @Override
        public String toString() {
            return "ckdm_npc:" + npcId.toLowerCase(Locale.ROOT);
        }
    }
}
