/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class KillCounter extends Helper implements UnitDestroyedListener {

    Map<String, Map<String, Integer>> kills;

    String path = "unitStats";

    public KillCounter(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUnitDestroyedListener(this);
        kills = new HashMap();
        
        command.debug("Loading unitStats...");
        try {
            path = new File(".").getCanonicalPath().replaceAll("pring.*", "pring").replace('\\', '/') + "/unitStats";
            FileInputStream fileIn = new FileInputStream(path);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            kills = (Map<String, Map<String, Integer>>) in.readObject();
            in.close();
            fileIn.close();
            command.debug("Loaded unitStats for " + kills.size() + " units.");
            for (Map.Entry<String, Map<String, Integer>> a : kills.entrySet()){
                command.debug("-- " + a.getKey());
                for (Map.Entry<String, Integer> b : a.getValue().entrySet()){
                    command.debug(a.getKey() + " -> " + b.getKey() + " = " + b.getValue());
                }
            }
        } catch (Exception e) {
            command.debug("Loading unitStats failed!\n",e);
        }

    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    @Override
    public void update(int frame) {
        if (frame % 500 == 0) {
            command.debug("Saving...");
            try {
                FileOutputStream fileOut = new FileOutputStream(path);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(kills);
                out.close();
                fileOut.close();
                command.debug("Saved to " + path);
            } catch (IOException i) {
                command.debug("Saving failed!");
            }
        }
    }

    public float getEfficiency(UnitDef attacker, UnitDef enemy) {
        if (attacker == null || enemy == null) return 1;
        String a = attacker.getName();
        String b = enemy.getName();
        if (!kills.containsKey(a))kills.put(a, new HashMap());
        if (!kills.containsKey(b))kills.put(b, new HashMap());
        if (!kills.get(a).containsKey(b))kills.get(a).put(b, 1);
        if (!kills.get(b).containsKey(a))kills.get(b).put(a, 1);
        return kills.get(a).get(b) / (float) kills.get(b).get(a);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        if (killer == null) {
            command.debug("Unknown friendly killer.");
            return;
        }
        unitDestroyed(e.getDef(), killer.getUnit().getDef());
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        if (e == null) {
            command.debug("Unknown hostile killer.");
            return;
        }
        if (!u.getUnit().isParalyzed()) {
            unitDestroyed(u.getUnit().getDef(), e.getDef());
        }
    }

    private void unitDestroyed(UnitDef attacker, UnitDef killer) {
        //counter
        /*
        if (!kills.containsKey(-1)) {
            kills.put(-1, new HashMap());
        }
        if (!kills.get(-1).containsKey(-1)) {
            kills.get(-1).put(-1, 0);
        }
        kills.get(-1).put(-1, kills.get(-1).get(-1) + 1);*/
        //actual stuff:
        if (!kills.containsKey(killer.getName())) {
            kills.put(killer.getName(), new HashMap());
        }
        if (!kills.get(killer.getName()).containsKey(attacker.getName())) {
            kills.get(killer.getName()).put(attacker.getName(), 1);
        }
        kills.get(killer.getName()).put(attacker.getName(),
                kills.get(killer.getName()).get(attacker.getName()) + (int) attacker.getCost(command.metal));
        command.debug( ": Efficiency of " + killer.getHumanName() + " against " + attacker.getHumanName() + 
                ": " + getEfficiency(killer, attacker));
    }

}
