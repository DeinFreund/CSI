/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Resource;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponDef;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.Timer;
import zkcbai.helpers.DefenseManager;
import zkcbai.helpers.EconomyManager;
import zkcbai.helpers.KillCounter;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.CommanderHandler;
import zkcbai.unitHandlers.FactoryHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.helpers.LosManager;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.RadarManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.unitHandlers.BuilderHandler;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.NanoHandler;
import zkcbai.unitHandlers.SlasherHandler;
import zkcbai.unitHandlers.TurretHandler;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.FakeEnemy;
import zkcbai.unitHandlers.units.FakeMex;
import zkcbai.unitHandlers.units.FakeSlasher;
import zkcbai.unitHandlers.units.tasks.BuildTask;

/**
 *
 * @author User
 */
public class Command implements AI {

    public static final boolean LOG_TO_INFOLOG = false;

    private OOAICallback clbk;
    private int ownTeamId;
    public Resource metal;
    public Resource energy;

    public LosManager losManager;
    public RadarManager radarManager;
    public ZoneManager areaManager;
    public DefenseManager defenseManager;
    public Pathfinder pathfinder;
    public KillCounter killCounter;
    public EconomyManager economyManager;

    private final Collection<EnemyEnterRadarListener> enemyEnterRadarListeners = new HashSet<>();
    private final Collection<EnemyEnterLOSListener> enemyEnterLOSListeners = new HashSet<>();
    private final Collection<EnemyLeaveRadarListener> enemyLeaveRadarListeners = new HashSet<>();
    private final Collection<EnemyLeaveLOSListener> enemyLeaveLOSListeners = new HashSet<>();
    private final Collection<EnemyDiscoveredListener> enemyDiscoveredListeners = new HashSet<>();
    private final Collection<UnitFinishedListener> unitFinishedListeners = new HashSet<>();
    private final Collection<UnitCreatedListener> unitCreatedListeners = new HashSet<>();
    private final Collection<UnitDestroyedListener> unitDestroyedListeners = new HashSet<>();
    private final Collection<UpdateListener> updateListeners = new HashSet<>();
    private final Collection<UnitDamagedListener> unitDamagedListeners = new HashSet<>();
    private final TreeMap<Integer, Set<UpdateListener>> singleUpdateListeners = new TreeMap<>();

    private Collection<CommanderHandler> comHandlers = new HashSet<>();
    private FactoryHandler facHandler;
    private FighterHandler fighterHandler;
    private SlasherHandler slasherHandler;
    private TurretHandler turretHandler;
    private BuilderHandler builderHandler;
    private NanoHandler nanoHandler;

    private final Map<Integer, AIUnit> units = new HashMap<>();
    private final Map<Integer, Enemy> enemies = new HashMap<>();
    private final Set<UnitDef> enemyDefs = new HashSet<>();
    private final Set<FakeEnemy> fakeEnemies = new HashSet<>();
    private final Map<BuildTask, Area> buildTasks = new HashMap<>();
    private final TreeMap<Float, UnitDef> defSpeedMap = new TreeMap<>();

    private int frame;
    private AIFloat3 startPos = null;

    public Command(int teamId, OOAICallback callback) {
        try {

            this.clbk = callback;
            ownTeamId = teamId;
            metal = clbk.getResources().get(0);
            energy = clbk.getResources().get(1);
            economyManager = new EconomyManager(this, clbk);
            losManager = new LosManager(this, clbk);
            areaManager = new ZoneManager(this, clbk);
            radarManager = new RadarManager(this, clbk);
            defenseManager = new DefenseManager(this, clbk);
            pathfinder = new Pathfinder(this, clbk);
            killCounter = new KillCounter(this, clbk);

            fighterHandler = new FighterHandler(this, clbk);
            slasherHandler = new SlasherHandler(this, clbk);
            turretHandler = new TurretHandler(this, clbk);
            facHandler = new FactoryHandler(this, callback); //req: fighterHandler
            builderHandler = new BuilderHandler(this, clbk);
            nanoHandler = new NanoHandler(this, clbk);

            economyManager.init();
            losManager.init();
            radarManager.init();
            defenseManager.init();
            pathfinder.init();
            killCounter.init();
            areaManager.init();

            mexradius = clbk.getUnitDefByName("cormex").getRadius();

            String[] importantSpeedDefs = new String[]{"bomberdive", "fighter", "corawac", "corvamp", "blackdawn", "armbrawl", "armpw", "armflea", "corak"};
            for (String s : importantSpeedDefs) {
                defSpeedMap.put(clbk.getUnitDefByName(s).getSpeed(), clbk.getUnitDefByName(s));
            }
            //clbk.getGame().sendStartPosition(true, new  AIFloat3(5588, 0 , 1100));
            debug(clbk.getUnitDefByName("armcom1").getSpeed());
            debug("ZKCBAI successfully initialized.");

        } catch (Throwable e) {
            debug("Exception in init:", e);
            //throw new RuntimeException();// no point in continuing execution
        }
    }

    public FactoryHandler getFactoryHandler() {
        return facHandler;
    }

    public BuilderHandler getBuilderHandler() {
        return builderHandler;
    }

    public NanoHandler getNanoHandler() {
        return nanoHandler;
    }

    public FighterHandler getFighterHandler() {
        return fighterHandler;
    }

    public Collection<CommanderHandler> getCommanderHandlers() {
        return comHandlers;
    }

    public TreeMap<Float, UnitDef> getEnemyUnitDefSpeedMap() {
        return defSpeedMap;
    }

    /**
     *
     * @param u
     * @return aiunit which contains u or null if such an aiunit doesnt exist
     */
    public AIUnit getAIUnit(Unit u) {
        if (u == null) {
            return null;
        }
        return units.get(u.getUnitId());
    }

    /**
     * Returns all known enemies
     *
     * @param allEnemies decides whether to include timed out enemies
     * @return
     */
    public synchronized Collection<Enemy> getEnemyUnits(boolean allEnemies) {
        if (allEnemies) {
            return enemies.values();
        }
        List<Enemy> list = new ArrayList();
        for (Enemy e : enemies.values()) {
            if (allEnemies || !e.isTimedOut()) {
                list.add(e);
            }
        }
        return list;
    }

    /**
     * Returns own units.
     *
     * @return
     */
    public Collection<AIUnit> getUnits() {

        return units.values();
    }

    private Random random = new Random();
    int lastRandomUnitUpdate = -100000;
    List<AIUnit> unitList;

    public AIUnit getRandomUnit() {
        if (units.isEmpty()) {
            return null;
        }
        if (getCurrentFrame() - lastRandomUnitUpdate > 300) {
            unitList = new ArrayList(getUnits());
            lastRandomUnitUpdate = getCurrentFrame();
        }
        AIUnit randomUnit;
        do {
            randomUnit = unitList.get(random.nextInt(unitList.size()));
        } while (randomUnit.isDead());
        return randomUnit;
    }

    int lastRandomEnemyUpdate = -100000;
    List<Enemy> enemyList;

    /**
     *
     * @return null if no enemy known
     */
    public Enemy getRandomEnemy() {
        if (getCurrentFrame() - lastRandomEnemyUpdate > 150 || enemyList.isEmpty()) {
            enemyList = new ArrayList(getEnemyUnits(true));
            lastRandomEnemyUpdate = getCurrentFrame();
        }
        if (enemyList.isEmpty()) {
            return null;
        }
        Enemy randomEnemy;
        do {
            randomEnemy = enemyList.get(random.nextInt(enemyList.size()));
        } while (!randomEnemy.isAlive());
        return randomEnemy;
    }

    /**
     * Returns enemy unit in radius that haven't timed out
     *
     * @param pos
     * @param radius
     * @return
     */
    public Set<Enemy> getEnemyUnitsIn(AIFloat3 pos, float radius) {
        Set<Enemy> list = new HashSet();
        for (Unit u : clbk.getEnemyUnitsIn(pos, radius)) {
            if (enemies.containsKey(u.getUnitId())) {
                list.add(enemies.get(u.getUnitId()));
            }
        }
        return list;
    }

    /**
     * Returns enemy unit in radius that haven't timed out
     *
     * @param pos
     * @param radius
     * @return
     */
    public Set<Enemy> getEnemyUnitsIn_Slow(AIFloat3 pos, float radius) {
        Set<Enemy> list = new HashSet();
        for (Area a : areaManager.getAreas()) {
            if (a.distanceTo(pos) > radius) {
                continue;
            }
            for (Enemy e : a.getNearbyEnemies()) {
                if (e == null) {
                    continue;
                }
                if (e.distanceTo(pos) < radius && !e.isTimedOut()) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    public Set<UnitDef> getEnemyUnitDefs() {
        return enemyDefs;
    }

    public int getCurrentFrame() {
        return frame;
    }

    public OOAICallback getCallback() {
        return clbk;
    }

    public void addFakeEnemy(FakeEnemy e) {
        fakeEnemies.add(e);
        enemyDiscovered(e);
    }

    public void removeFakeEnemy(FakeEnemy e) {
        fakeEnemies.remove(e);
        enemyDestroyed(e, null);
    }

    public void addEnemyEnterRadarListener(EnemyEnterRadarListener listener) {
        enemyEnterRadarListeners.add(listener);
    }

    public void addEnemyDiscoveredListener(EnemyDiscoveredListener listener) {
        enemyDiscoveredListeners.add(listener);
    }

    public void addEnemyEnterLOSListener(EnemyEnterLOSListener listener) {
        enemyEnterLOSListeners.add(listener);
    }

    public void addEnemyLeaveRadarListener(EnemyLeaveRadarListener listener) {
        enemyLeaveRadarListeners.add(listener);
    }

    public void addEnemyLeaveLOSListener(EnemyLeaveLOSListener listener) {
        enemyLeaveLOSListeners.add(listener);
    }

    public void removeEnemyEnterRadarListener(EnemyEnterRadarListener listener) {
        enemyEnterRadarListeners.remove(listener);
    }

    public void removeEnemyDiscoveredListener(EnemyDiscoveredListener listener) {
        enemyDiscoveredListeners.remove(listener);
    }

    public void removeEnemyEnterLOSListener(EnemyEnterLOSListener listener) {
        enemyEnterLOSListeners.remove(listener);
    }

    public void removeEnemyLeaveRadarListener(EnemyLeaveRadarListener listener) {
        enemyLeaveRadarListeners.remove(listener);
    }

    public void removeEnemyLeaveLOSListener(EnemyLeaveLOSListener listener) {
        enemyLeaveLOSListeners.remove(listener);
    }

    public void addUnitFinishedListener(UnitFinishedListener listener) {
        unitFinishedListeners.add(listener);
    }

    public void removeUnitFinishedListener(UnitFinishedListener listener) {
        unitFinishedListeners.remove(listener);
    }

    public void addUnitCreatedListener(UnitCreatedListener listener) {
        unitCreatedListeners.add(listener);
    }

    public void removeUnitCreatedListener(UnitCreatedListener listener) {
        unitCreatedListeners.remove(listener);
    }

    public void addUnitDestroyedListener(UnitDestroyedListener listener) {
        unitDestroyedListeners.add(listener);
    }

    public void removeUnitDestroyedListener(UnitDestroyedListener listener) {
        unitDestroyedListeners.remove(listener);
    }

    public void addUnitDamagedListener(UnitDamagedListener listener) {
        unitDamagedListeners.add(listener);
    }

    public void removeUnitDamagedListener(UnitDamagedListener listener) {
        unitDamagedListeners.remove(listener);
    }

    public boolean addSingleUpdateListener(UpdateListener listener, int frame) {
        if (frame >= 1000000000) {
            return false;
        }
        Set<UpdateListener> list = singleUpdateListeners.get(frame);
        if (list == null) {
            singleUpdateListeners.put(frame, new HashSet());
        }
        singleUpdateListeners.get(frame).add(listener);
        return true;
    }

    public void removeSingleUpdateListener(UpdateListener listener, int frame) {
        singleUpdateListeners.get(frame).remove(listener);
    }

    public void addUpdateListener(UpdateListener listener) {
        updateListeners.add(listener);
    }

    public AIFloat3 getStartPos() {
        return new AIFloat3(startPos);
    }

    private void checkForMetal(String luamsg) {
        List<AIFloat3> availablemetalspots = new ArrayList();
        for (String spotDesc : luamsg.substring(13, luamsg.length() - 2).split("},\\{")) {
            float x, y, z;
            y = Float.parseFloat(spotDesc.split(",")[0].split(":")[1]);
            x = Float.parseFloat(spotDesc.split(",")[1].split(":")[1]);
            z = Float.parseFloat(spotDesc.split(",")[3].split(":")[1]);
            availablemetalspots.add(new AIFloat3(x, y, z));
        }

        debug("Received " + availablemetalspots.size() + " mex spots via luaMessage.");
        areaManager.setMexSpots(availablemetalspots);
    }

    @Override
    public int luaMessage(String inData) {
        try {
            if (inData.length() > 11 && inData.substring(0, 11).equalsIgnoreCase("METAL_SPOTS")) {
                checkForMetal(inData);
            }
            return 0;
        } catch (Throwable e) {
            debug("Exception in luaMessage: ", e);
        }
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        try {
            unitFinished(unit);
        } catch (Throwable e) {
            debug("Exception in unitGiven: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        try {
            Enemy att = null;
            if (attacker == null && getEnemyUnitsIn(unit.getPos(), 800).isEmpty() && getEnemyUnitsIn_Slow(unit.getPos(), 1200).isEmpty() && dir.lengthSquared() > 0.01){
                AIFloat3 npos = new AIFloat3(unit.getPos());
                dir.normalize();
                dir.scale(400);
                npos.add(dir);
                addFakeEnemy(new FakeSlasher(npos, this, clbk));
            }
            if (attacker != null) {
                att = enemies.get(attacker.getUnitId());
            }
            AIUnit def = units.get(unit.getUnitId());
            if (def == null) {
                if (unit.isBeingBuilt()) {
                    return 0;
                }
                mark(unit.getPos(), "unknown unit");
                if (unit.getDef() != null && unit.getHealth() > 0f) unitFinished(unit);
                return 0;
            }
            for (UnitDamagedListener listener : unitDamagedListeners) {
                listener.unitDamaged(def, att, damage);
            }
            def.damaged(att, damage);
            if (unit.getHealth() <= 0) {
                unitDestroyed(unit, attacker);
            }
        } catch (Throwable e) {
            debug("Exception in unitDamaged: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int enemyEnterLOS(Unit enemy) {
        try {
            if (!enemies.containsKey(enemy.getUnitId())) {
                enemyDiscovered(new Enemy(enemy, this, clbk));
            }
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            FakeEnemy closest = null;
            for (FakeEnemy fe : fakeEnemies) {
                if (fe.isUnit(enemy) && (closest == null || closest.distanceTo(enemy.getPos()) > fe.distanceTo(enemy.getPos()))) {
                    closest = fe;
                }
            }
            if (closest != null) {
                unitDestroyed(enemy, null);
                aiEnemy = closest;
                closest.setUnitId(enemy.getUnitId());
                closest.setUnit(enemy);
                enemies.remove(closest.hashCode());
                enemies.put(closest.getUnitId(), closest);
                fakeEnemies.remove(closest);
            }
            if (!enemyDefs.contains(enemy.getDef())) {
                enemyDefs.add(enemy.getDef());
                debug("New enemy UnitDef: " + enemy.getDef().getHumanName());
                if (!defSpeedMap.containsKey(enemy.getMaxSpeed())) {
                    defSpeedMap.put(enemy.getMaxSpeed(), enemy.getDef());
                }
            }
            aiEnemy.enterLOS();
            Collection<EnemyEnterLOSListener> listenerc = new ArrayList(enemyEnterLOSListeners);
            for (EnemyEnterLOSListener listener : listenerc) {
                listener.enemyEnterLOS(aiEnemy);
            }
//            debug("Health, Speed, Cost: ");
//            debug(enemy.getHealth() + "/" + enemy.getMaxHealth());
//            debug(enemy.getSpeed() + "/" + enemy.getMaxSpeed());
//            debug(enemy.getVel().length());
//            debug(enemy.getDef().getCost(metal));

        } catch (Throwable e) {
            debug("Exception in enemyEnterLOS: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int enemyLeaveLOS(Unit enemy) {
        try {
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            if (aiEnemy != null) {
                aiEnemy.leaveLOS();
                Collection<EnemyLeaveLOSListener> listenerc = new ArrayList(enemyLeaveLOSListeners);
                for (EnemyLeaveLOSListener listener : listenerc) {
                    listener.enemyLeaveLOS(aiEnemy);
                }
            }
        } catch (Throwable e) {
            debug("Exception in enemyLeaveLOS: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int enemyEnterRadar(Unit enemy) {
        try {
            //debug(enemy.getUnitId() + " entered radar");
            if (!enemies.containsKey(enemy.getUnitId())) {
                enemyDiscovered(new Enemy(enemy, this, clbk));
            }
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            //debug("ai enemy is null " + (aiEnemy == null));
            aiEnemy.enterRadar();
            Collection<EnemyEnterRadarListener> listenerc = new ArrayList(enemyEnterRadarListeners);
            for (EnemyEnterRadarListener listener : listenerc) {
                listener.enemyEnterRadar(aiEnemy);
            }
        } catch (Throwable e) {
            debug("Exception in enemyEnterRadar: ", e);
        }
        return 0;
    }

    public synchronized void enemyDiscovered(Enemy enemy) {

        // debug("ai enemy is null " + (enemy == null));
        enemies.put(enemy.getUnitId(), enemy);
        for (EnemyDiscoveredListener listener : new ArrayList<EnemyDiscoveredListener>(enemyDiscoveredListeners)) {
            listener.enemyDiscovered(enemy);
        }
    }

    @Override
    public synchronized int enemyLeaveRadar(Unit enemy) {
        try {
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            if (aiEnemy != null) {
                aiEnemy.leaveRadar();
                for (EnemyLeaveRadarListener listener : enemyLeaveRadarListeners) {
                    listener.enemyLeaveRadar(aiEnemy);
                }
            }
        } catch (Throwable e) {
            debug("Exception in enemyLeaveRadar: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        try {

            AIUnit att = null;
            if (attacker != null) {
                att = units.get(attacker.getUnitId());
            }
            Enemy def = enemies.get(enemy.getUnitId());
            for (UnitDamagedListener listener : unitDamagedListeners) {
                listener.unitDamaged(def, att, damage);
            }
            if (enemy.getHealth() <= 0) {
                unitDestroyed(enemy, attacker);
            }
        } catch (Throwable e) {
            debug("Exception in enemyDamaged: ", e);
        }
        return 0;
    }

    @Override
    public synchronized int unitDestroyed(Unit unit, Unit attacker) {
        try {
            AIUnit aiunit = units.get(unit.getUnitId());
            if (aiunit != null) {

                Enemy enemy = null;
                if (attacker != null && enemies.containsKey(attacker.getUnitId())) {
                    enemy = enemies.get(attacker.getUnitId());
                }
                Collection<UnitDestroyedListener> unitDestroyedListenersc = new ArrayList(unitDestroyedListeners);
                for (UnitDestroyedListener listener : unitDestroyedListenersc) {
                    listener.unitDestroyed(aiunit, enemy);
                }
                aiunit.destroyed();
                units.remove(unit.getUnitId());

            }
            Enemy e = enemies.get(unit.getUnitId());
            if (e != null) {
                enemyDestroyed(e, attacker);
            }
        } catch (Throwable e) {
            debug("Exception in unitDestroyed: ", e);
        }
        return 0;
    }

    public synchronized void enemyDestroyed(Enemy e, Unit attacker) {
        Collection<UnitDestroyedListener> unitDestroyedListenersc = new ArrayList(unitDestroyedListeners);
        AIUnit frenemy = null;
        if (attacker != null && units.containsKey(attacker.getUnitId())) {
            frenemy = units.get(attacker.getUnitId());
        }
        for (UnitDestroyedListener listener : unitDestroyedListenersc) {
            listener.unitDestroyed(e, frenemy);
        }
        if (enemies.remove(e.getUnitId()) == null && !(e instanceof FakeEnemy)) {
            throw new AssertionError("Enemy not in enemy map");
        }
        e.destroyed();
    }

    @Override
    public int init(int teamId, OOAICallback callback) {
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        try {
            if (units.containsKey(unit.getUnitId())) {
                units.get(unit.getUnitId()).moveFailed();
            } else {
                debug("avoided nullpointer exception in unit move failed");
            }
        } catch (Throwable e) {
            debug("Exception in unitMoveFailed: ", e);
        }
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        try {
            /*if (!units.containsKey(unit.getUnitId())) {
             //                debug("IDLEBUG Exception unknown unit " + unit.getUnitId());
             return 0;
             }*/
            if (!units.containsKey(unit.getUnitId())) {
                mark(unit.getPos(), "zombie?");
                debug("zombie " + unit.getUnitId());
                if (unit.getDef() != null && unit.getHealth() > 0) unitFinished(unit);
                return 0;
            }
            units.get(unit.getUnitId()).idle();
        } catch (Throwable e) {
            debug("Exception in unitIdle: ", e);
        }
        return 0;
    }

    @Override
    public int update(int frame) {
        Timer timer = new Timer(5000, null);
        try {
            final Thread mainThread = Thread.currentThread();
            timer.setRepeats(false);
            timer.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent ae) {
                    debug("Lagging out, printing stack trace: ", false);
                    for (StackTraceElement ste : mainThread.getStackTrace()) {
                        debug(ste.toString(), false);
                    }
                    mainThread.stop(new RuntimeException("Lockup"));
                }
            });
            timer.start();
            this.frame = frame;
            for (UpdateListener listener : updateListeners) {
                long time = System.currentTimeMillis();
                listener.update(frame);
                time = System.currentTimeMillis() - time;
                if (time > 100) {
                    debug("Update of " + listener.getClass().getName() + " took " + time + " ms.");
                }
            }
            while (!singleUpdateListeners.isEmpty() && singleUpdateListeners.firstKey() <= frame) {
                for (UpdateListener listener : singleUpdateListeners.firstEntry().getValue()) {
                    long time = System.currentTimeMillis();
                    listener.update(frame);
                    time = System.currentTimeMillis() - time;
                    if (time > 100) {
                        debug("Update of " + listener.getClass().getName() + " took " + time + " ms.");
                    }
                }
                singleUpdateListeners.remove(singleUpdateListeners.firstKey());
            }

            //Check for forgotten units
            if (frame % 242 == 0) {
                for (AIUnit u : units.values()) {
                    u.checkIdle();
                }
            }
        } catch (Throwable e) {
            debug("Exception in update: ", e);
        } finally {
            timer.stop();
        }

        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builderunit) {
        debug("unit created");
        try {
            AIUnit builder = builderunit == null ? null : units.get(builderunit.getUnitId());

            Collection<UnitCreatedListener> unitCreatedListenersClone = new ArrayList(unitCreatedListeners);
            for (UnitCreatedListener listener : unitCreatedListenersClone) {
                listener.unitCreated(unit, builder);
            }

        } catch (Throwable e) {
            debug("Exception in unitCreated: ", e);

        }
        debug("unit created finished");
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        debug("unit finished");
        try {
//            debug("IDLEBUG finished " + unit.getUnitId());
            if (startPos == null) {
                startPos = unit.getPos();
            }
            AIUnit aiunit;
            switch (unit.getDef().getName()) {
                case "armnanotc":
                    aiunit = nanoHandler.addUnit(unit);
                    break;
                default:
                    if (unit.getDef().getCustomParams().containsKey("commtype")) {
                        debug("Identified a commander with unitdef: " + unit.getDef().getName());
                        CommanderHandler comHandler = new CommanderHandler(this, clbk);
                        comHandlers.add(comHandler);
                        aiunit = comHandler.addUnit(unit);
                        break;
                    }
                    if (unit.getDef().getBuildOptions().size() > 5) {
                        if (unit.getDef().getSpeed() < 0.1) {
                            aiunit = facHandler.addUnit(unit);
                        } else {
                            aiunit = builderHandler.addUnit(unit);
                        }
                        break;
                    }
                    if (unit.getDef().getName().equalsIgnoreCase("cormist")) {
                        aiunit = slasherHandler.addUnit(unit);
                        break;
                    }
                    if (unit.getDef().isAbleToAttack() && unit.getDef().getSpeed() > 0) {
                        aiunit = fighterHandler.addUnit(unit);
                        break;
                    }
                    if (unit.getDef().isAbleToAttack() && unit.getDef().getSpeed() < 0.1) {
                        aiunit = turretHandler.addUnit(unit);
                        break;
                    }
                    //debug("Unused UnitDef " + unit.getDef().getName() + " in UnitFinished");
                    aiunit = new AIUnit(unit, this);
            }
            //shadow expansion
            if (aiunit.getDef().equals(clbk.getUnitDefByName("cormex"))) {
                float mindist = Float.MAX_VALUE;
                Mex closest = null;
                for (Mex m : areaManager.getMexes()) {
                    if (m.getOwner() == ZoneManager.Owner.enemy) {
                        continue;
                    }
                    float dist = 0;
                    for (Mex em : areaManager.getMexes()) {
                        if (em.getOwner() != ZoneManager.Owner.enemy) {
                            continue;
                        }
                        dist += em.distanceTo(m.pos);
                    }
                    if (dist < mindist) {
                        mindist = dist;
                        closest = m;
                    }
                }
                if (closest != null) {
                    FakeEnemy fakemex = new FakeMex(closest.pos, this, clbk);
                    addFakeEnemy(fakemex);
                    closest.setEnemyMex(fakemex);
                }
            }
//            debug("IDLEBUG registering " + aiunit.getUnit().getUnitId());
            units.put(unit.getUnitId(), aiunit);
            Collection<UnitFinishedListener> unitFinishedListenersClone = new ArrayList(unitFinishedListeners);
            for (UnitFinishedListener listener : unitFinishedListenersClone) {
                listener.unitFinished(aiunit);
            }
//            debug("IDLEBUG calling idle after UnitFinished " + aiunit.getUnit().getUnitId());
            units.get(unit.getUnitId()).idle();
        } catch (Throwable e) {
            debug("Exception in unitFinished: ", e);
        }
        debug("unit finished finished");
        return 0;

    }

    public void debug(String s, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        debug(s + sw.toString());
    }

    public void debug(Integer s) {
        debug(s.toString());
    }

    public void debug(Float s) {
        debug(s.toString());
    }

    PrintWriter debugWriter;

    public void debugStackTrace() {
        try {
            throw new Exception("DebugStackTrace");
        } catch (Exception ex) {
            debug("trace: ", ex);
        }
    }

    public synchronized void debug(String s) {
        debug(s, true);
    }

    public synchronized void debug(String s, boolean intoInfolog) {
        if (intoInfolog && LOG_TO_INFOLOG) {
            clbk.getGame().sendTextMessage(s, 0);
        }
        try {
            if (debugWriter == null) {
                if (new File("CSI.log").exists()) {
                    new File("CSI.log").delete();
                }
                debugWriter = new PrintWriter(new BufferedWriter(new FileWriter("CSI.log", true)));
            }
            debugWriter.println("[" + String.format("%06d", getCurrentFrame()) + "] " + s);
            debugWriter.flush();

        } catch (IOException e) {

        }
        //mark(new AIFloat3(), s); // if debug doesnt echo
    }

    public void mark(AIFloat3 pos, String s) {
        if (LOG_TO_INFOLOG) clbk.getMap().getDrawer().addPoint(pos, s);
        debug("add mark at " + pos.toString() + ": " + s);
    }

    public void registerBuildTask(BuildTask bt) {
        if (bt == null) throw new NullPointerException("BuildTask is null");
        buildTasks.put(bt, areaManager.getArea(bt.getPos()));
        buildTasks.get(bt).getBuildTasks().add(bt);
        if (buildTasks.size() > 50) {
            debug(buildTasks.size() + " BuildTasks registered");
        }
    }

    public void clearBuildTask(BuildTask bt) {
        if (!buildTasks.containsKey(bt)) {
            debug("Unregistered BuildTask for " + bt.getBuilding().getHumanName());
            return;
        }
        buildTasks.get(bt).getBuildTasks().remove(bt);
        buildTasks.remove(bt);
    }

    private float mexradius;

    public boolean isPossibleToBuildAt(UnitDef building, AIFloat3 pos, int facing) {
        boolean ret = clbk.getMap().isPossibleToBuildAt(building, pos, facing);
        if (!ret || building.getName().equalsIgnoreCase("cormex")) {
            return ret;
        }
        List<BuildTask> outdated = new ArrayList();
        for (BuildTask bt : areaManager.getArea(pos).getNearbyBuildTasks()) {
            if (bt.isBeingWorkedOn(getCurrentFrame() - 30 * 60 * 5)) {
                if (distance2D(bt.getPos(), pos) < bt.getBuilding().getRadius() + building.getRadius() + 50) {
                    return false;
                }
            } else {
                outdated.add(bt);
            }
        }
        for (BuildTask bt : outdated) {
            buildTasks.remove(bt);
        }
        //debug(areaManager.getMexes().size() + " mexes");
        float mindist = Float.MAX_VALUE;
        for (Mex m : areaManager.getArea(pos).getNearbyMexes()) {
            mindist = Math.min(mindist, m.distanceTo(pos));
            if (m.distanceTo(pos) < mexradius + 1.5 * building.getRadius() && !m.isBuilt()) {
                return false;
            }
        }
        for (AIUnit au : facHandler.getUnits()) {
            if (au.distanceTo(pos) < 200 + building.getRadius() * 1.5) {
                return false;
            }
        }
        //debug("checking for " + building.getHumanName() +" closed mex has a distance of " + mindist);
        return true;
    }

    public static float distance(AIFloat3 a, AIFloat3 b) {
        AIFloat3 delta = new AIFloat3(a);
        delta.sub(b);
        return delta.length();
    }

    public static float distance2D(AIFloat3 a, AIFloat3 b) {
        AIFloat3 delta = new AIFloat3(a);
        delta.sub(b);
        delta.y = 0;
        return delta.length();
    }
}
