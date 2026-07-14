package wtf.opal.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import wtf.opal.client.binding.repository.BindRepository;
import wtf.opal.client.command.impl.config.ConfigCommand;
import wtf.opal.client.command.impl.misc.DashboardCommand;
import wtf.opal.client.command.impl.misc.ScriptCommand;
import wtf.opal.client.command.impl.module.BindCommand;
import wtf.opal.client.command.impl.module.ToggleCommand;
import wtf.opal.client.command.impl.player.FriendCommand;
import wtf.opal.client.command.impl.player.UsernameCommand;
import wtf.opal.client.command.impl.player.movement.HClipCommand;
import wtf.opal.client.command.impl.player.movement.VClipCommand;
import wtf.opal.client.command.repository.CommandRepository;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.hypixel.TransactionStreamValidator;
import wtf.opal.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.opal.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.opal.client.feature.helper.impl.player.swing.SwingDelay;
import wtf.opal.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.opal.client.feature.helper.impl.render.FadingBlockHelper;
import wtf.opal.client.feature.helper.impl.render.ScreenPositionManager;
import wtf.opal.client.feature.module.impl.combat.*;
import wtf.opal.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.impl.movement.*;
import wtf.opal.client.feature.module.impl.movement.clipper.ClipperModule;
import wtf.opal.client.feature.module.impl.movement.flight.FlightModule;
import wtf.opal.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.opal.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.opal.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.opal.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.opal.client.feature.module.impl.utility.*;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.impl.utility.disabler.impl.MinibloxDisabler;
import wtf.opal.client.feature.module.impl.utility.inventory.AutoArmorModule;
import wtf.opal.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.opal.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.opal.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.opal.client.feature.module.impl.visual.*;
import wtf.opal.client.feature.module.impl.visual.esp.ESPModule;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.impl.world.FastBreakModule;
import wtf.opal.client.feature.module.impl.world.SilenceTellyModule;
import wtf.opal.client.feature.module.impl.world.TimerModule;
import wtf.opal.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.client.notification.NotificationManager;
import wtf.opal.client.music.MusicPlayerModule;
import wtf.opal.client.music.MusicService;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.client.PostClientInitializationEvent;

import wtf.opal.scripting.repository.ScriptRepository;
import wtf.opal.utility.data.SaveUtility;

import java.util.ServiceLoader;

public final class OpalClient {

    private final NotificationManager notificationManager;
    private final BindRepository bindRepository;

    private CommandRepository commandRepository;
    private ModuleRepository moduleRepository;
    private ScriptRepository scriptRepository;
    private MusicService musicService;

    private boolean postInitialization;

    private OpalClient() {
        this.notificationManager = new NotificationManager();
        this.bindRepository = new BindRepository();
    }

    public void runPostInitializations() {
        this.runHelperInitializations();
//        this.registerFabricEvents();

        if (this.musicService == null) {
            this.musicService = new MusicService();
        }

        if (this.moduleRepository == null) {
            this.moduleRepository = ModuleRepository.fromModules(
                    // Combat
                    new KillAuraModule(),
                    new TeamsModule(),
                    new BacktrackModule(),
                    new BlockModule(),
                    new ReachModule(),
                    new PiercingModule(),
                    new AutoClickerModule(),
                    new AttackDelayModule(),
                    new CriticalsModule(),
                    new VelocityModule(),
                    new FakeLagModule(),
                    new AutoHeadModule(),
                    new CrystalAuraModule(),
                    // Visual
                    new ClickGUIModule(),
                    new FullBrightModule(),
                    new AnimationsModule(),
                    new OverlayModule(),
                    new ChamsModule(),
                    new ESPModule(),
                    new BreakProgressModule(),
                    new CapeModule(),
                    new AmbienceModule(),
                    new AttackEffectsModule(),
                    new TabGUIModule(),
                    new StreamerModeModule(),
                    new TitleChangerModule(),
//                    new DiscordRPCModule(),
                    new NoHurtCameraModule(),
                    new NoFOVModule(),
                    new PostProcessingModule(),
                    // World
                    new ScaffoldModule(),
                    new SilenceTellyModule(),
                    new TimerModule(),
                    new BreakerModule(),
                    new FastBreakModule(),
                    new AntiStaffModule(),
                    // Movement
                    new FlightModule(),
                    new SpeedModule(),
                    new JumpCooldownModule(),
                    new SprintModule(),
                    new MovementFixModule(),
                    new NoSlowModule(),
                    new InventoryMoveModule(),
                    new FastWebModule(),
                    new TargetStrafeModule(),
                    new PhaseModule(),
                    new LongJumpModule(),
                    new FastStopModule(),
                    new StrafeModule(),
                    new PhysicsModule(),
                    new SpiderModule(),
                    new ClipperModule(),
                    new SafeWalkModule(),
                    new StuckModule(),
                    // Utility
                    new FastUseModule(),
                    new MusicPlayerModule(),
                    new NoFallModule(),
                    new AutoBucketModule(),
                    new ChestStealerModule(),
                    new InventoryManagerModule(),
                    new AutoArmorModule(),
                    new DisablerModule(),
                    new FastPlaceModule(),
                    new AntiVoidModule(),
                    new AutoToolModule(),
                    new AntiTNTModule(),
                    new AutoChestModule(),
                    new AutoHypixelModule(),
                    new BlinkModule(),
                    new PingSpoofModule(),
                    new AntiBotsModule(),
                    new KillSayModule(),
                    new NoRotateModule(),
                    new SpammerModule(),
                    new PartySpamModule()
            );
        }

        SaveUtility.loadBindings();
        SaveUtility.loadConfigFile("default");

        if (this.commandRepository == null) {
            this.commandRepository = CommandRepository.builder()
                    .putAll(
                            new ToggleCommand(),
                            new BindCommand(),
                            new ConfigCommand(),
                            new VClipCommand(),
                            new HClipCommand(),
                            new UsernameCommand(),
                            new DashboardCommand(),
                            new FriendCommand(),
                            new ScriptCommand()
                    ).build();
        }

        if (this.scriptRepository == null) {
            this.scriptRepository = new ScriptRepository();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown));

        this.postInitialization = true;
        EventDispatcher.dispatch(new PostClientInitializationEvent());

        PayloadTypeRegistry.playS2C().register(PhysicsModule.ResyncPhysicsPayload.ID, PhysicsModule.ResyncPhysicsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MinibloxDisabler.MovePayload.ID, MinibloxDisabler.MovePayload.CODEC);
    }

    
    private void runHelperInitializations() {
        LocalDataWatch.setInstance();
        MouseHelper.setInstance();
        SwingDelay.setInstance();
        SlotHelper.setInstance();
        TimerHelper.setInstance();
        FadingBlockHelper.setInstance();
        ScreenPositionManager.setInstance();
        TransactionStreamValidator.setInstance();
    }

    
    private void registerFabricEvents() {
//        ScreenEvents.BEFORE_INIT.register(((client, screen, scaledWidth, scaledHeight) -> {
//            if (screen instanceof TitleScreen) {
//                mc.setScreen(new OpalTitleScreen());
//            }
//        }));
    }

    
    private void onShutdown() {
        this.moduleRepository.getModule(ClickGUIModule.class).setEnabled(false);
        this.moduleRepository.getModule(MusicPlayerModule.class).setEnabled(false);

        if (this.musicService != null) {
            this.musicService.close();
        }

        SaveUtility.saveConfig("default");
        SaveUtility.saveBindings();
    }

    
    public boolean isPostInitialization() {
        return postInitialization;
    }

    public ModuleRepository getModuleRepository() {
        return moduleRepository;
    }

    public BindRepository getBindRepository() {
        return bindRepository;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ScriptRepository getScriptRepository() {
        return scriptRepository;
    }

    public MusicService getMusicService() {
        return musicService;
    }

    private static OpalClient instance;

    public static OpalClient getInstance() {
        if (instance == null) {
            instance = new OpalClient();
        }
        return instance;
    }
    
    public static void setInstance() {
        instance = new OpalClient();
    }

}
