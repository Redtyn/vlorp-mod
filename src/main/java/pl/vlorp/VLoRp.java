package pl.vlorp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class VLoRp implements ModInitializer {
    public static final String ID = "vlorp";
    public static Item HOMEWORK, DIARY, BELL, ORDER;

    @Override
    public void onInitialize() {
        Sched.init();

        HOMEWORK = reg("homework", new ActionItem(new Item.Settings().maxCount(16), 20, (w, p) ->
            p.sendMessage(Text.literal("§6[ДЗ] §f" + Quotes.pick(Quotes.HOMEWORK)), false)));

        DIARY = reg("diary", new ActionItem(new Item.Settings().maxCount(1), 20, (w, p) ->
            p.sendMessage(Text.literal("§9[Librus] §f" + Quotes.pick(Quotes.DIARY)), false)));

        BELL = reg("bell", new ActionItem(new Item.Settings().maxCount(1), 60, (w, p) -> {
            MinecraftServer s = w.getServer();
            s.getPlayerManager().broadcast(Text.literal("§e§l🔔 ДЗЫНЬ! §fЗвонок. Либо на урок, либо на волю. Выбор за вами."), false);
            sound(w, p, SoundEvents.BLOCK_BELL_USE);
            for (ServerPlayerEntity o : s.getPlayerManager().getPlayerList())
                o.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
        }));

        ORDER = reg("order", new ActionItem(new Item.Settings().maxCount(1), 600, (w, p) -> {
            spawnDirector(w, p.getPos().add(p.getRotationVec(1f).multiply(6, 0, 6)));
            p.getServer().getPlayerManager().broadcast(
                Text.literal("§4§lПан Дырэктор §cпроснулся. Вам не повезло."), false);
        }));

        Registry.register(Registries.ITEM_GROUP, Identifier.of(ID, "main"),
            FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.vlorp"))
                .icon(() -> new ItemStack(HOMEWORK))
                .entries((ctx, e) -> { e.add(HOMEWORK); e.add(DIARY); e.add(BELL); e.add(ORDER); })
                .build());

        // Директор бьёт — директор орёт
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp && source.getAttacker() != null
                && source.getAttacker().getCommandTags().contains("direktor")) {
                sp.sendMessage(Text.literal(Quotes.pick(Quotes.DIRECTOR)), false);
            }
            return true;
        });

        // Директор умер
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getCommandTags().contains("direktor") && entity.getServer() != null) {
                entity.getServer().getPlayerManager().broadcast(
                    Text.literal("§a§lПан Дырэктор §aушёл на пенсию (в Незер). Но он вернётся в понедельник."), false);
            }
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {

            d.register(CommandManager.literal("rola")
                .then(CommandManager.argument("rola", StringArgumentType.word())
                    .suggests((c, b) -> CommandSource.suggestMatching(new String[]{"uchenik", "uchitel"}, b))
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        boolean teacher = StringArgumentType.getString(ctx, "rola").equals("uchitel");
                        setRole(p, teacher);
                        return 1;
                    })));

            d.register(CommandManager.literal("dz").executes(ctx -> {
                ctx.getSource().sendMessage(Text.literal("§6[ДЗ] §f" + Quotes.pick(Quotes.HOMEWORK)));
                return 1;
            }));

            d.register(CommandManager.literal("pereklichka").executes(ctx -> {
                MinecraftServer s = ctx.getSource().getServer();
                s.getPlayerManager().broadcast(Text.literal("§e§lПЕРЕКЛИЧКА:"), false);
                for (int i = 0; i < 4; i++)
                    s.getPlayerManager().broadcast(Text.literal("§f" + Quotes.pick(Quotes.ROLL_CALL)), false);
                return 1;
            }));

            d.register(CommandManager.literal("zvonok").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                ((ActionItem) BELL).use(p.getServerWorld(), p, net.minecraft.util.Hand.MAIN_HAND);
                p.getServer().getPlayerManager().broadcast(Text.literal("§eЗвонок прозвенел (без предмета тоже можно)."), false);
                return 1;
            }));

            d.register(CommandManager.literal("direktor").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                spawnDirector(p.getServerWorld(), p.getPos().add(3, 0, 3));
                p.getServer().getPlayerManager().broadcast(
                    Text.literal("§4§lПан Дырэктор §cвышел из кабинета. Он без приглашения."), false);
                return 1;
            }));

            d.register(CommandManager.literal("k_doske")
                .then(CommandManager.argument("uchenik", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity caller = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "uchenik");
                    Vec3d front = caller.getPos().add(caller.getRotationVec(1f).multiply(2, 0, 2));
                    target.teleport(caller.getServerWorld(), front.x, front.y, front.z,
                        java.util.Set.of(), caller.getYaw() + 180f, 0f);
                    title(target, "§c§lК ДОСКЕ!", "§fРешай. Все смотрят. Все смеются.");
                    sound(caller.getServerWorld(), target, SoundEvents.ENTITY_VILLAGER_NO);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
                    return 1;
                })));

            d.register(CommandManager.literal("dvoika")
                .then(CommandManager.argument("uchenik", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "uchenik");
                    title(target, "§4§l2", "§fДвойка. Родителей вызывать не будем, мы их уже нашли.");
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 1));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 1));
                    sound(target.getServerWorld(), target, SoundEvents.ENTITY_WITHER_HURT);
                    return 1;
                })));

            d.register(CommandManager.literal("urok").executes(ctx -> {
                MinecraftServer s = ctx.getSource().getServer();
                s.getPlayerManager().broadcast(Text.literal("§e§lУРОК НАЧАЛСЯ. §fМатан. 45 минут. Выхода нет."), false);
                s.getPlayerManager().getPlayerList().forEach(p ->
                    title(p, "§e§lУРОК", "§fСидим, ждём."));
                Sched.after(1200, () -> {
                    s.getPlayerManager().broadcast(Text.literal("§c§lКОНТРОЛЬНАЯ! §fБез предупреждения. Как всегда."), false);
                    s.getPlayerManager().getPlayerList().forEach(p -> {
                        title(p, "§c§lКОНТРОЛЬНАЯ", "§fЛист А4. Ручка. Молитва.");
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
                    });
                });
                Sched.after(2400, () -> {
                    s.getPlayerManager().broadcast(Text.literal("§a§lПЕРЕМЕНА. §fУ вас 10 минут, чтобы списать ДЗ."), false);
                    s.getPlayerManager().getPlayerList().forEach(p ->
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 2)));
                });
                return 1;
            }));
        });
    }

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(ID, name), item);
    }

    private static void sound(ServerWorld w, PlayerEntity p, SoundEvent ev) {
        w.playSound(null, p.getBlockPos(), ev, SoundCategory.PLAYERS, 2f, 1f);
    }

    private static void title(ServerPlayerEntity p, String title, String sub) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
    }

    private static void spawnDirector(ServerWorld w, Vec3d pos) {
        VindicatorEntity dir = EntityType.VINDICATOR.create(w);
        if (dir == null) return;
        dir.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0f, 0f);
        dir.setCustomName(Text.literal("Пан Дырэктор").formatted(Formatting.DARK_RED, Formatting.BOLD));
        dir.setCustomNameVisible(true);
        dir.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        dir.addCommandTag("direktor");
        dir.setPersistent();
        dir.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(60);
        dir.setHealth(60f);
        dir.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 1));
        w.spawnEntity(dir);
    }

    private static void setRole(ServerPlayerEntity p, boolean teacher) {
        Scoreboard sb = p.getServer().getScoreboard();
        String name = teacher ? "vlo_uchitel" : "vlo_uchenik";
        Team t = sb.getTeam(name);
        if (t == null) t = sb.addTeam(name);
        t.setPrefix(Text.literal(teacher ? "[Учитель] " : "[Ученик] ")
            .formatted(teacher ? Formatting.RED : Formatting.GREEN));
        sb.addScoreHolderToTeam(p.getNameForScoreboard(), t);

        if (teacher) {
            p.giveItemStack(new ItemStack(BELL));
            p.giveItemStack(new ItemStack(DIARY));
            p.giveItemStack(new ItemStack(ORDER));
            ItemStack ptr = new ItemStack(Items.STICK);
            ptr.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Указка"));
            p.giveItemStack(ptr);
            p.sendMessage(Text.literal("§cТы теперь Учитель. Команды: /k_doske, /dvoika, /urok, /pereklichka, /direktor"), false);
        } else {
            p.giveItemStack(new ItemStack(DIARY));
            p.giveItemStack(new ItemStack(HOMEWORK, 5));
            p.sendMessage(Text.literal("§aТы теперь Ученик. Команды: /dz, /pereklichka. Беги от директора."), false);
        }
    }
}
