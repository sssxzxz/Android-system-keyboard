package com.sssxzxz.productkeyboard;

import android.os.Looper;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SuggestionQueryTest extends InstrumentationTestCase {
    public void testSlowEditorDoesNotBlockMainAndOnlyLatestQueryPublishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), delivered = new CountDownLatch(1);
        SuggestionQuery[] query = new SuggestionQuery[1];
        AtomicInteger stale = new AtomicInteger(), reads = new AtomicInteger();
        try {
            getInstrumentation().runOnMainSync(() -> {
                query[0] = new SuggestionQuery();
                BaseInputConnection slow = new BaseInputConnection(new View(getInstrumentation().getTargetContext()),true) {
                    @Override public CharSequence getSelectedText(int flags) {
                        assertNotSame(Looper.getMainLooper(),Looper.myLooper());
                        entered.countDown();
                        try { release.await(5,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return "";
                    }
                };
                query[0].request(slow,Arrays.asList("old"),false,(matches,text) -> stale.incrementAndGet());
            });
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            AtomicInteger clicks = new AtomicInteger();
            getInstrumentation().runOnMainSync(() -> {
                KeyboardButton button = new KeyboardButton(getInstrumentation().getTargetContext());
                button.setOnClickListener(v -> clicks.incrementAndGet()); button.performClick();
                BaseInputConnection latest = new BaseInputConnection(new View(getInstrumentation().getTargetContext()),true) {
                    @Override public CharSequence getSelectedText(int flags) { reads.incrementAndGet(); return ""; }
                    @Override public CharSequence getTextBeforeCursor(int n,int flags) { return "089"; }
                };
                for (int i=0;i<20;i++) query[0].request(latest,Arrays.asList("089-A"),false,(matches,text) -> stale.incrementAndGet());
                query[0].request(latest,Arrays.asList("089-B"),false,(matches,text) -> {
                    assertSame(Looper.getMainLooper(),Looper.myLooper());
                    assertEquals(Arrays.asList("089-B"),matches); delivered.countDown();
                });
            });
            assertEquals(1,clicks.get()); assertEquals(1L,release.getCount());
            release.countDown();
            assertTrue(delivered.await(3,TimeUnit.SECONDS));
            assertEquals(0,stale.get()); assertEquals(1,reads.get());
        } finally {
            release.countDown();
            getInstrumentation().runOnMainSync(() -> { if(query[0]!=null) query[0].close(); });
        }
    }
    public void testDisconnectedEditorReturnsEmptyCandidates() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1); SuggestionQuery[] query = new SuggestionQuery[1];
        try {
            getInstrumentation().runOnMainSync(() -> {
                query[0] = new SuggestionQuery();
                BaseInputConnection broken = new BaseInputConnection(new View(getInstrumentation().getTargetContext()),true) {
                    @Override public CharSequence getSelectedText(int flags) { throw new IllegalStateException("Editor closed"); }
                };
                query[0].request(broken,Arrays.asList("089"),false,(matches,text) -> {
                    assertTrue(matches.isEmpty()); delivered.countDown();
                });
            });
            assertTrue(delivered.await(3,TimeUnit.SECONDS));
        } finally { getInstrumentation().runOnMainSync(() -> query[0].close()); }
    }
}
