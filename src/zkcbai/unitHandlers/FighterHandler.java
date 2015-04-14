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
import java.util.HashSet;
import java.util.Map;
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

    private void useUnit(AIUnit au) {
        float sum = 0;
        for (Area a : command.areaManager.getAreas()){
            if (a.getZone() == ZoneManager.Zone.hostile || a.getZone() == ZoneManager.Zone.fortified) continue;
            float imp = a.getDanger()*a.getValue()/a.getEnemies().size();
            for (Enemy e: a.getEnemies()){
                sum += Math.max(imp,0.000001) * au.getEfficiencyAgainst(e) / au.distanceTo(e.getPos());
            }
        }
        float random = rnd.nextFloat() * sum;
        for (Area a : command.areaManager.getAreas()){
            if (a.getZone() == ZoneManager.Zone.hostile || a.getZone() == ZoneManager.Zone.fortified) continue;
            float imp = a.getDanger()*a.getValue()/a.getEnemies().size();
            for (Enemy e: a.getEnemies()){
                random -= Math.max(imp,0.000001) * au.getEfficiencyAgainst(e) / au.distanceTo(e.getPos());
                if (random < 0){
                    au.assignTask(new AttackTask(e,command.getCurrentFrame() + 60, this, command));
                    return;
                }
            }
        }
        au.assignTask(new FightTask(new AIFloat3(rnd.nextFloat()*clbk.getMap().getWidth()*8,0,rnd.nextFloat()*clbk.getMap().getHeight()*8), 
                command.getCurrentFrame()+100, this));
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

        command.getFactoryHandler().requestAssault(5);

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

        command.getFactoryHandler().requestAssault(5);

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
    public void troopIdle(AISquad s) {
    }

    @Override
    public void areaZoneChange(Area area, ZoneManager.Zone prev, ZoneManager.Zone next) {
        if (next == ZoneManager.Zone.fortified) {
            command.getFactoryHandler().requestAssault(2);
        }
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
