package com.sssxzxz.productkeyboard;

import java.util.*;

/** A fully occupied 5x5 board; 0 and its tail are always adjacent on one row. */
final class KeyLayout {
    static List<String> defaults() {
        return new ArrayList<>(Arrays.asList(
            "clear","custom2","1","2","3",
            "custom0","custom3","4","5","6",
            "custom1","custom4","7","8","9",
            "minus","custom5","0","tail","search",
            "delete","custom6","custom7","custom8","custom9"));
    }
    static boolean valid(List<String> keys) {
        if(keys==null || keys.size()!=25 || !new HashSet<>(keys).equals(new HashSet<>(defaults()))) return false;
        int zero=keys.indexOf("0");
        return zero%5<4 && keys.get(zero+1).equals("tail");
    }
    static List<String> move(List<String> keys,String source,int target) {
        if(!valid(keys) || target<0 || target>=25 || source.equals("tail") || !keys.contains(source)) return null;
        List<String> next=new ArrayList<>(keys); int from=keys.indexOf(source);
        String destination=keys.get(target);
        if(source.equals("0") || destination.equals("0") || destination.equals("tail")) {
            int old=keys.indexOf("0"), at=source.equals("0")?target:from;
            if(at%5==4) return null;
            List<String> displaced=new ArrayList<>();
            for(int pos:new int[]{at,at+1}) if(pos!=old && pos!=old+1) displaced.add(keys.get(pos));
            next.set(at,"0"); next.set(at+1,"tail");
            for(int pos:new int[]{old,old+1}) if(pos!=at && pos!=at+1) next.set(pos,displaced.remove(0));
        } else Collections.swap(next,from,target);
        return valid(next)?next:null;
    }
    static List<String> mirror(List<String> keys) {
        List<String> next=new ArrayList<>(keys);
        for(int row=0;row<5;row++) for(int col=0;col<5;col++) next.set(row*5+col,keys.get(row*5+4-col));
        int tail=next.indexOf("tail"), zero=next.indexOf("0");
        next.set(tail,"0"); next.set(zero,"tail"); return next;
    }
}
