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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.helpers.DefenseManager;
import zkcbai.helpers.KillCounter;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.CommanderHandler;
import zkcbai.unitHandlers.FactoryHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.helpers.LosManager;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.PlaceholderEnemies;
import zkcbai.helpers.RadarManager;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class Command implements AI {

    private final OOAICallback clbk;
    private final int ownTeamId;
    public final Resource metal;
    public final Resource energy;

    public final LosManager losManager;
    public final RadarManager radarManager;
    public final ZoneManager areaManager;
    public final DefenseManager defenseManager;
    public final Pathfinder pathfinder;
    public final KillCounter killCounter;
    public final PlaceholderEnemies placeholderEnemies;

    private final Collection<EnemyEnterRadarListener> enemyEnterRadarListeners = new HashSet();
    private final Collection<EnemyEnterLOSListener> enemyEnterLOSListeners = new HashSet();
    private final Collection<EnemyLeaveRadarListener> enemyLeaveRadarListeners = new HashSet();
    private final Collection<EnemyLeaveLOSListener> enemyLeaveLOSListeners = new HashSet();
    private final Collection<EnemyDiscoveredListener> enemyDiscoveredListeners = new HashSet();
    private final Collection<UnitFinishedListener> unitFinishedListeners = new HashSet();
    private final Collection<UnitDestroyedListener> unitDestroyedListeners = new HashSet();
    private final Collection<UpdateListener> updateListeners = new HashSet();
    private final Collection<UnitDamagedListener> unitDamagedListeners = new HashSet();
    private final TreeMap<Integer, Set<UpdateListener>> singleUpdateListeners = new TreeMap();

    private final Collection<CommanderHandler> comHandlers = new HashSet();
    private final FactoryHandler facHandler;
    private final FighterHandler fighterHandler;

    private final Map<Integer, AIUnit> units = new TreeMap();
    private final Map<Integer, Enemy> enemies = new TreeMap();
    private final Set<UnitDef> enemyDefs = new HashSet();
    private final TreeMap<Float, UnitDef> defSpeedMap = new TreeMap();

    private int frame;
    private AIFloat3 startPos = null;

    public Command(int teamId, OOAICallback callback) {
        try {
            
            this.clbk = callback;
            ownTeamId = teamId;
            facHandler = new FactoryHandler(this, callback);
            fighterHandler = new FighterHandler(this, clbk);
            metal = clbk.getResources().get(0);
            energy = clbk.getResources().get(1);
            losManager = new LosManager(this, clbk);
            areaManager = new ZoneManager(this, clbk);
            radarManager = new RadarManager(this, clbk);
            defenseManager = new DefenseManager(this, clbk);
            pathfinder = new Pathfinder(this, clbk);
            killCounter = new KillCounter(this, clbk);
            placeholderEnemies = new PlaceholderEnemies(this, clbk);
            

            String[] importantSpeedDefs = new String[]{"bomberdive", "fighter", "corawac", "corvamp", "blackdawn", "armbrawl", "armpw"};
            for (String s : importantSpeedDefs) {
                defSpeedMap.put(clbk.getUnitDefByName(s).getSpeed(), clbk.getUnitDefByName(s));
            }
            debug(clbk.getUnitDefByName("armcom1").getSpeed());
            debug("ZKCBAI successfully initialized.");

        } catch (Exception e) {
            debug("Exception in init:", e);
            throw new RuntimeException();// no point in continuing execution
        }
    }

    public FactoryHandler getFactoryHandler() {
        return facHandler;
    }
    
    public FighterHandler getFighterHandler() {
        return fighterHandler;
    }

    public TreeMap<Float, UnitDef> getEnemyUnitDefSpeedMap() {
        return defSpeedMap;
    }

    /**
     *  Returns all known enemies
     * @param allEnemies decides whether to include timed out enemies
     * @return
     */
    public List<Enemy> getEnemyUnits(boolean allEnemies) {
        List<Enemy> list = new ArrayList();
        for (Enemy e : enemies.values()) {
            if (allEnemies || !e.isTimedOut()) list.add(e);
        }
        return list;
    }
    
    /**
     *  Returns own units.
     * @return
     */
    public Collection<AIUnit> getUnits() {
        
        return units.values();
    }

    /**
     * Returns enemy unit in radius that haven't timed out
     * @param pos
     * @param radius
     * @return
     */
    public List<Enemy> getEnemyUnitsIn(AIFloat3 pos, float radius) {
        List<Enemy> list = new ArrayList();
        /*for (Unit u : clbk.getEnemyUnitsIn(pos, radius)){
         list.add(enemies.get(u.getUnitId()));
         }*/
        for (Enemy e : enemies.values()) {
            if (e.distanceTo(pos) < radius && !e.isTimedOut()) {
                list.add(e);
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

    public void addUnitFinishedListener(UnitFinishedListener listener) {
        unitFinishedListeners.add(listener);
    }

    public void removeUnitFinishedListener(UnitFinishedListener listener) {
        unitFinishedListeners.remove(listener);
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
        } catch (Exception e) {
            debug("Exception in luaMessage: ", e);
        }
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        try {
            unitFinished(unit);
        } catch (Exception e) {
            debug("Exception in unitGiven: ", e);
        }
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        try {
            debug("unitdamaged");
            Enemy att = null;
            if (attacker != null) att = enemies.get(attacker.getUnitId());
            AIUnit def = units.get(unit.getUnitId());
            for (UnitDamagedListener listener : unitDamagedListeners){
                listener.unitDamaged(def, att, damage);
            }
            if (unit.getHealth() <=0){
                unitDestroyed(unit, attacker);
            }
        } catch (Exception e) {
            debug("Exception in unitDamaged: ", e);
        }
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        try {
            if (!enemies.containsKey(enemy.getUnitId())) {
                enemyDiscovered(new Enemy(enemy, this, clbk));
            }
            if (!enemyDefs.contains(enemy.getDef())) {
                enemyDefs.add(enemy.getDef());
                debug("New enemy UnitDef: " + enemy.getDef().getHumanName());
                if (!defSpeedMap.containsKey(enemy.getMaxSpeed())) {
                    defSpeedMap.put(enemy.getMaxSpeed(), enemy.getDef());
                }
            }
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
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

        } catch (Exception e) {
            debug("Exception in enemyEnterLOS: ", e);
        }
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        try {
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            if (aiEnemy != null) {
                aiEnemy.leaveLOS();
                Collection<EnemyLeaveLOSListener> listenerc = new ArrayList(enemyLeaveLOSListeners);
                for (EnemyLeaveLOSListener listener : listenerc) {
                    listener.enemyLeaveLOS(aiEnemy);
                }
            }
        } catch (Exception e) {
            debug("Exception in enemyLeaveLOS: ", e);
        }
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        try {
            debug(enemy.getUnitId() + " entered radar");
            if (!enemies.containsKey(enemy.getUnitId())) {
                enemyDiscovered(new Enemy(enemy, this, clbk));
            }
            debug("discovered");
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            debug("ai enemy is null " + (aiEnemy == null));
            aiEnemy.enterRadar();
            Collection<EnemyEnterRadarListener> listenerc = new ArrayList(enemyEnterRadarListeners);
            for (EnemyEnterRadarListener listener : listenerc) {
                listener.enemyEnterRadar(aiEnemy);
            }
        } catch (Exception e) {
            debug("Exception in enemyEnterRadar: ", e);
        }
        return 0;
    }

    public void enemyDiscovered(Enemy enemy) {

        // debug("ai enemy is null " + (enemy == null));
        if (enemy.getUnit() != null)
            enemies.put(enemy.getUnit().getUnitId(), enemy);
        for (EnemyDiscoveredListener listener : new ArrayList<EnemyDiscoveredListener>(enemyDiscoveredListeners)) {
            listener.enemyDiscovered(enemy);
        }
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        try {
            Enemy aiEnemy = enemies.get(enemy.getUnitId());
            if (aiEnemy != null) {
                aiEnemy.leaveRadar();
                for (EnemyLeaveRadarListener listener : enemyLeaveRadarListeners) {
                    listener.enemyLeaveRadar(aiEnemy);
                }
            }
        } catch (Exception e) {
            debug("Exception in enemyLeaveRadar: ", e);
        }
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        try {
            
            AIUnit att = null;
            if (attacker != null) att = units.get(attacker.getUnitId());
            Enemy def = enemies.get(enemy.getUnitId());
            for (UnitDamagedListener listener : unitDamagedListeners){
                listener.unitDamaged(def, att, damage);
            }
            if (enemy.getHealth() <= 0) {
                unitDestroyed(enemy, attacker);
            }
        } catch (Exception e) {
            debug("Exception in enemyDamaged: ", e);
        }
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        try {
            AIUnit aiunit = units.get(unit.getUnitId());
            if (aiunit != null) {
                aiunit.destroyed();

                Enemy enemy = null;
                if (attacker != null && enemies.containsKey(attacker.getUnitId())) {
                    enemy = enemies.get(attacker.getUnitId());
                }
                Collection<UnitDestroyedListener> unitDestroyedListenersc = new ArrayList(unitDestroyedListeners);
                for (UnitDestroyedListener listener : unitDestroyedListenersc) {
                    listener.unitDestroyed(aiunit, enemy);
                }
                units.remove(unit.getUnitId());

            }
            Enemy e = enemies.get(unit.getUnitId());
            if (e != null) {
                e.destroyed();

                Collection<UnitDestroyedListener> unitDestroyedListenersc = new ArrayList(unitDestroyedListeners);
                AIUnit frenemy = null;
                if (attacker != null && units.containsKey(attacker.getUnitId())) {
                    frenemy = units.get(attacker.getUnitId());
                }
                for (UnitDestroyedListener listener : unitDestroyedListenersc) {
                    listener.unitDestroyed(e, frenemy);
                }
                enemies.remove(unit.getUnitId());
            }
        } catch (Exception e) {
            debug("Exception in unitDestroyed: ", e);
        }
        return 0;
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
        } catch (Exception e) {
            debug("Exception in unitMoveFailed: ", e);
        }
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        try {
            if (!units.containsKey(unit.getUnitId())) {
//                debug("IDLEBUG Exception unknown unit " + unit.getUnitId());
                return 0;
            }
            units.get(unit.getUnitId()).idle();
        } catch (Exception e) {
            debug("Exception in unitIdle: ", e);
        }
        return 0;
    }

    @Override
    public int update(int frame) {
        try {
            this.frame = frame;
            for (UpdateListener listener : updateListeners) {
                listener.update(frame);
            }
            while (!singleUpdateListeners.isEmpty() && singleUpdateListeners.firstKey() <= frame) {
                for (UpdateListener listener : singleUpdateListeners.firstEntry().getValue()) {
                    listener.update(frame);
                }
                singleUpdateListeners.remove(singleUpdateListeners.firstKey());
            }

            //Check for forgotten units
            if (frame % 242 == 0) {
                for (AIUnit u : units.values()) {
                    u.checkIdle();
                }
            }
        } catch (Exception e) {
            debug("Exception in update: ", e);
        }
        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builder) {
        try {
        } catch (Exception e) {
            debug("Exception in unitCreated: ", e);
        }
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        try {
//            debug("IDLEBUG finished " + unit.getUnitId());
            if (startPos == null) {
                startPos = unit.getPos();
            }
            AIUnit aiunit;
            switch (unit.getDef().getName()) {
                case "armcom1":
                    CommanderHandler comHandler = new CommanderHandler(this, clbk);
                    comHandlers.add(comHandler);
                    aiunit = comHandler.addUnit(unit);
                    break;
                default:
                    if (unit.getDef().getBuildOptions().size() > 0 && unit.getDef().getSpeed() < 0.1){
                        aiunit = facHandler.addUnit(unit);
                        break;
                    }
                    if (!unit.getDef().isAbleToRepair() && unit.getDef().getSpeed() > 0) {
                        aiunit = fighterHandler.addUnit(unit);
                        break;
                    }
                    debug("Unused UnitDef " + unit.getDef().getName() + " in UnitFinished");
                    aiunit = new AIUnit(unit, null);
            }
//            debug("IDLEBUG registering " + aiunit.getUnit().getUnitId());
            units.put(unit.getUnitId(), aiunit);
            Collection<UnitFinishedListener> unitFinishedListenersClone = new ArrayList(unitFinishedListeners);
            for (UnitFinishedListener listener : unitFinishedListenersClone) {
                listener.unitFinished(aiunit);
            }
//            debug("IDLEBUG calling idle after UnitFinished " + aiunit.getUnit().getUnitId());
            units.get(unit.getUnitId()).idle();
        } catch (Exception e) {
            debug("Exception in unitFinished: ", e);
        }
        return 0;

    }

    public void debug(String s, Exception e) {
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

    public void debug(String s) {
        clbk.getGame().sendTextMessage(s, 0);
    }

    public void mark(AIFloat3 pos, String s) {
        clbk.getMap().getDrawer().addPoint(pos, s);
    }
}
