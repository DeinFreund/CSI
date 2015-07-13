/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.squads.RaiderSquad;
import zkcbai.unitHandlers.squads.ScoutSquad;
import zkcbai.unitHandlers.squads.SquadHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.EnemyEnterLOSListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaZoneChangeListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.squads.AssaultSquad;
import zkcbai.unitHandlers.squads.GenericSquad;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.tasks.AssaultTask;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.FightTask;

/**
 *
 * @author User
 */
public class FighterHandler extends UnitHandler implements EnemyDiscoveredListener, EnemyEnterLOSListener, AreaZoneChangeListener, UpdateListener {

    public FighterHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addEnemyDiscoveredListener(this);
        cmd.addEnemyEnterLOSListener(this);
        cmd.addUpdateListener(this);

    }

    Set<SquadHandler> squads = new HashSet();

    Map<Integer, SquadHandler> unitSquads = new TreeMap();

    boolean scouting = false;
    private Random rnd = new Random();

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        useUnit(au);
        return au;
    }

    private class pqEntry {

        final float strength;
        final float efficiency;
        final AIUnit unit;

        public pqEntry(float strength, float efficiency, AIUnit unit) {
            this.strength = strength;
            this.efficiency = efficiency;
            this.unit = unit;
        }
    }
    
    int lastAssaultCheck = 0;

    private void tryAssault() {
        if (command.getCurrentFrame() - lastAssaultCheck < 150) return;
        lastAssaultCheck = command.getCurrentFrame();
        for (Area a : command.areaManager.getAreas()) {
            if (!(a.isFront() || (a.getZone() != ZoneManager.Zone.hostile && a.getEnemies().size() > 0))) {
                continue;
            }
            float strength = 0;
            Comparator<pqEntry> pqComp = new Comparator<pqEntry>() {

                @Override
                public int compare(pqEntry t, pqEntry t1) {
                    if (t == null && t1 == null) {
                        return 0;
                    }
                    if (t == null) {
                        return -1;
                    }
                    if (t1 == null) {
                        return 1;
                    }
                    return (int) Math.signum(t1.efficiency - t.efficiency);
                }

            };

            PriorityQueue<pqEntry> pq = new PriorityQueue(1, pqComp);
            for (AIUnit own : aiunits.values()) {
                if (a.getNearbyEnemies().isEmpty()){
                    strength += own.getDef().getCost(command.metal);
                    pq.add(new pqEntry(own.getDef().getCost(command.metal),1, own));
                    continue;
                }
                float val = own.getDef().getCost(command.metal) / a.getNearbyEnemies().size();
                float ss = strength;
                for (Enemy e : a.getNearbyEnemies()) {
                    strength += val * Math.min(3, own.getEfficiencyAgainst(e));
                }
                pq.add(new pqEntry(strength - ss, (strength -ss)/own.getDef().getCost(command.metal), own));
            }
            float enemyStrength = 0;
            for (Enemy e: a.getNearbyEnemies()){
                enemyStrength += e.getMetalCost();
            }
            if (strength > enemyStrength * 2 - a.getValue() * 1000 && strength > 250){
                strength = 0;
                AISquad squad = new AISquad(this);
                while ((strength <= enemyStrength * 1.2 || strength < 250) && !pq.isEmpty()){
                    squad.addUnit(pq.peek().unit);
                    aiunits.remove(pq.peek().unit.getUnit().getUnitId());
                    strength += pq.poll().strength;
                }
                squad.assignTask(new AssaultTask(a.getPos(), command, this).setInfo("assault"));
                command.debug("Launching assault with " + squad.getUnits().size() + " units.");
                command.mark(a.getPos(), "assaulting " + a.getNearbyEnemies().size() + " units");
            }
            
        }
    }
    
    private void useUnit(AIUnit au) {
        tryAssault();
        float sum = 0;
        for (Area a : command.areaManager.getAreas()){
            if (a.getZone() != ZoneManager.Zone.own) continue;
            float imp = a.getDanger()*a.getValue()/a.getEnemies().size();
            for (Enemy e: a.getEnemies()){
                sum += Math.max(imp,0.000001) * au.getEfficiencyAgainst(e) / au.distanceTo(e.getPos());
            }
        }
        float random = rnd.nextFloat() * sum;
        int own = 0;
        for (Area a : command.areaManager.getAreas()){
            if (a.getZone() != ZoneManager.Zone.own || a.getValue() < 0.5) continue;
            own ++;
            float imp = a.getDanger()*a.getValue()/a.getEnemies().size();
            for (Enemy e: a.getEnemies()){
                random -= Math.max(imp,0.000001) * au.getEfficiencyAgainst(e) / au.distanceTo(e.getPos());
                if (random < 0){
                    au.assignTask(new AttackTask(e, command.getCurrentFrame() + 35, this, true, command));
                    return;
                }
            }
        }
        if (command.getBuilderHandler().getBuilders().size() > 0) {
            int counter = Math.abs(au.hashCode()) % command.getBuilderHandler().getBuilders().size();
            AIFloat3 tpos = null;
            for (AIUnit con : command.getBuilderHandler().getBuilders()) {
                counter--;
                if (counter < 0) {
                    tpos = new AIFloat3(con.getPos());
                    break;
                }
            }
            if (tpos != null) {

                au.assignTask(new FightTask(randomize(tpos, 300), command.getCurrentFrame() + 35, this));
                return;
            }
            command.debug("finding builder failed");
        }
        if (own > 0) {
            int fightA = rnd.nextInt(own);
            for (Area a : command.areaManager.getAreas()) {
                if (a.getZone() != ZoneManager.Zone.own || a.getValue() < 0.5) {
                    continue;
                }
                fightA--;
                if (fightA == 0) {
                    au.assignTask(new FightTask(a.getPos(), command.getCurrentFrame() + 35, this));
                    return;
                }

            }
        }
        au.assignTask(new FightTask(new AIFloat3(rnd.nextFloat() * clbk.getMap().getWidth() * 8, 0, rnd.nextFloat() * clbk.getMap().getHeight() * 8),
                command.getCurrentFrame() + 100, this));
        /*
         Area a;
         AIFloat3 target;
         SquadHandler rs;
        switch (au.getType()) {
            case raider:
                for (SquadHandler s : squads) {
                    if (s instanceof RaiderSquad && s.size() < 5 && s.timeTo(au) < 30 * 10) {
                        unitSquads.put(au.getUnit().getUnitId(), s);
                        s.addUnit(au);
                        return;
                    }
                }
                a = command.areaManager.getArea(au.getPos()).getNearestArea(command.areaManager.HOSTILE);
                target = null;
                if (a != null) {
                    target = a.getPos();
                }

                rs = new RaiderSquad(this, command, clbk, target);
                squads.add(rs);
                unitSquads.put(au.getUnit().getUnitId(), rs);
                rs.addUnit(au);
                break;
            case assault:
                for (SquadHandler s : squads) {
                    if (s instanceof AssaultSquad && s.size() < 6 && s.timeTo(au) < 30 * 10) {
                        unitSquads.put(au.getUnit().getUnitId(), s);
                        s.addUnit(au);
                        return;
                    }
                }
                a = command.areaManager.getArea(au.getPos()).getNearestArea(command.areaManager.FORTIFIED);
                target = null;
                if (a != null) {
                    target = a.getPos();
                }

                rs = new AssaultSquad(this, command, clbk, target);
                squads.add(rs);
                unitSquads.put(au.getUnit().getUnitId(), rs);
                rs.addUnit(au);
                break;
        }*/
    }

    public final Collection<AIUnit> getFighters(){
        return aiunits.values();
    }    
    @Override
    public void troopIdle(AIUnit u) {
        useUnit(u);
        //throw new RuntimeException("FighterHandler didn't dispatch unit to SquadHandler");
    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {
        if (t.getInfo().equals("assault")){
            command.debug("Finished assault");
            AssaultTask at = (AssaultTask)t;
            for (AITroop a : at.getAITroops()){
                if (a instanceof AISquad){
                    for (AIUnit au : a.getUnits().toArray(new AIUnit[a.getUnits().size()])){
                        ((AISquad)a).removeUnit(au, this);
                        aiunits.put(au.getUnit().getUnitId(),au);
                        a.idle();
                    }
                }
            }
        }
    }

    private AIFloat3 randomize(AIFloat3 f, float amt){
        return new AIFloat3(f.x+rnd.nextFloat()*amt-amt/2,f.y+rnd.nextFloat()*amt-amt/2,f.z+rnd.nextFloat()*amt-amt/2);
    }
    
    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
        if (!unitSquads.containsKey(u.getUnit().getUnitId())) {
            return;
        }
        unitSquads.get(u.getUnit().getUnitId()).removeUnit(u);
        unitSquads.remove(u.getUnit().getUnitId());
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }
    
    
    public AIFloat3 requestNewTarget(GenericSquad s) {
        Enemy best = null;
        AIFloat3 spos = s.getPos();
        Collection<Enemy> enemies = command.getEnemyUnitsIn(spos, 100000);
        float mindist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.distanceTo(spos) < mindist && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.HOSTILE_RAIDER_ACCESSIBLE);
        if (a != null) {
            return a.getPos();
        }

        int mintime = Integer.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.timeSinceLastSeen() < mintime && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mintime = e.timeSinceLastSeen();
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        ScoutSquad ss = new ScoutSquad(this, command, clbk);
        scouting = true;
        for (AIUnit u : s.disband()) {
            ss.addUnit(u);
            unitSquads.remove(u.getUnit().getUnitId());
            unitSquads.put(u.getUnit().getUnitId(), ss);
            u.getUnit().stop((short) 0, command.getCurrentFrame());
        }
        squads.add(ss);


        return new AIFloat3();
    }

    public AIFloat3 requestNewTarget(RaiderSquad s) {
        Enemy best = null;
        AIFloat3 spos = s.getPos();
        Collection<Enemy> enemies = command.getEnemyUnitsIn(spos, 100000);
        float mindist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.distanceTo(spos) < mindist && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.HOSTILE_RAIDER_ACCESSIBLE);
        if (a != null) {
            return a.getPos();
        }

        int mintime = Integer.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.timeSinceLastSeen() < mintime && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mintime = e.timeSinceLastSeen();
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        ScoutSquad ss = new ScoutSquad(this, command, clbk);
        scouting = true;
        for (AIUnit u : s.disband()) {
            ss.addUnit(u);
            unitSquads.remove(u.getUnit().getUnitId());
            unitSquads.put(u.getUnit().getUnitId(), ss);
            u.getUnit().stop((short) 0, command.getCurrentFrame());
        }
        squads.add(ss);


        return new AIFloat3();
    }

    public AIFloat3 requestNewTarget(AssaultSquad s) {

        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.FORTIFIED_ASSAULT_ACCESSIBLE);
        if (a != null) {
            return a.getPos();
        }

        Enemy best = null;
        AIFloat3 spos = s.getPos();
        Collection<Enemy> enemies = command.getEnemyUnitsIn(spos, 100000);
        float mindist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.distanceTo(spos) < mindist && command.defenseManager.isAssaultAccessible(e.getPos())
                    && command.defenseManager.getImmediateDanger(e.getPos()) > 0) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        int mintime = Integer.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.timeSinceLastSeen() < mintime && command.defenseManager.isAssaultAccessible(e.getPos())
                    && command.defenseManager.getImmediateDanger(e.getPos()) > 0) {
                mintime = e.timeSinceLastSeen();
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        ScoutSquad ss = new ScoutSquad(this, command, clbk);
        scouting = true;
        for (AIUnit u : s.disband()) {
            ss.addUnit(u);
            unitSquads.remove(u.getUnit().getUnitId());
            unitSquads.put(u.getUnit().getUnitId(), ss);
            u.getUnit().stop((short) 0, command.getCurrentFrame());
        }
        squads.add(ss);

        return new AIFloat3();
    }

    public void squadDestroyed(SquadHandler s) {
        squads.remove(s);
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void enemyDiscovered(Enemy e) {
        if (scouting) {
            scouting = false;
            Collection<SquadHandler> squadc = new ArrayList(squads);
            for (SquadHandler s : squadc) {
                if (s instanceof ScoutSquad) {
                    for (AIUnit u : s.disband()) {
                        unitSquads.remove(u.getUnit().getUnitId());
                        useUnit(u);
                    }
                }
            }
        }
    }

    public void requestReinforcements(SquadHandler rs) {
        float mindist = Float.MAX_VALUE;
        AIUnit best = null;
        SquadHandler bests = null;
        for (SquadHandler s : squads) {
            if (s.equals(rs)) {
                continue;
            }
            for (AIUnit u : s.getUnits()) {
                if (u.getType() == rs.getType() && u.distanceTo(rs.getPos()) < mindist) {
                    mindist = u.distanceTo(rs.getPos());
                    best = u;
                    bests = s;
                }
            }
        }
        if (best != null) {
            bests.removeUnit(best);
            unitSquads.remove(best.getUnit().getUnitId());
            unitSquads.put(best.getUnit().getUnitId(), rs);
            rs.addUnit(best);
        }
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
        enemyDiscovered(e);
    }

    @Override
    public void troopIdle(AISquad a) {
        for (AIUnit au : a.getUnits().toArray(new AIUnit[a.getUnits().size()])) {
            a.removeUnit(au, this);
            aiunits.put(au.getUnit().getUnitId(), au);
            troopIdle(au);
        }
    }

    @Override
    public void areaZoneChange(Area area, ZoneManager.Zone prev, ZoneManager.Zone next) {
    }

    private void counter(Enemy e){
        
    }
    
    @Override
    public void update(int frame) {
        if (frame % 30 == 0) {
            for (Enemy e : command.getEnemyUnitsIn(new AIFloat3(), 100000)) {
                counter(e);
            }
        }
    }

}
