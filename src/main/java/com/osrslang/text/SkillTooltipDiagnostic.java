package com.osrslang.text;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;

@Slf4j
public class SkillTooltipDiagnostic
{
    private final Client client;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int lastLogTick = 0;
    private int tick = 0;

    public SkillTooltipDiagnostic(Client client)
    {
        this.client = client;
    }

    public void update()
    {
        tick++;
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null)
        {
            return;
        }

        int mx = mouse.getX();
        int my = mouse.getY();

        boolean moved = mx != lastMouseX || my != lastMouseY;
        if (moved)
        {
            lastMouseX = mx;
            lastMouseY = my;
            lastLogTick = tick;
            log.info("Skill tooltip diagnostic: mouse at ({}, {})", mx, my);
        }

        if (!moved && tick - lastLogTick > 2 && tick % 20 != 0)
        {
            return;
        }

        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return;
        }

        Deque<Widget> queue = new ArrayDeque<>();
        Set<Widget> visited = new HashSet<>();
        List<Widget> candidates = new ArrayList<>();

        for (Widget root : roots)
        {
            if (root != null)
            {
                queue.add(root);
            }
        }

        // Only inspect widgets directly under the mouse.

        while (!queue.isEmpty())
        {
            Widget widget = queue.removeFirst();
            if (!visited.add(widget))
            {
                continue;
            }

            Rectangle bounds = widget.getBounds();
            if (!widget.isHidden()
                    && bounds != null
                    && bounds.x >= 0 && bounds.y >= 0
                    && bounds.width > 0 && bounds.height > 0
                    && bounds.contains(mx, my))
            {
                String name = widget.getName();
                String text = widget.getText();
                String targetVerb = widget.getTargetVerb();
                String[] actions = widget.getActions();

                if (!blank(name) || !blank(text) || !blank(targetVerb)
                        || (actions != null && actions.length > 0))
                {
                    candidates.add(widget);
                }
            }

            addChildren(queue, widget.getChildren());
            addChildren(queue, widget.getStaticChildren());
            addChildren(queue, widget.getDynamicChildren());
            addChildren(queue, widget.getNestedChildren());
        }

        candidates.sort(Comparator
                .comparing((Widget w) -> !w.getBounds().contains(mx, my))
                .thenComparingInt(w -> area(w.getBounds())));

        int limit = Math.min(candidates.size(), 30);
        for (int i = 0; i < limit; i++)
        {
            Widget widget = candidates.get(i);
            Rectangle bounds = widget.getBounds();
            boolean containsMouse = bounds.contains(mx, my);
            String name = widget.getName();
            String text = widget.getText();
            String targetVerb = widget.getTargetVerb();
            String[] actions = widget.getActions();

            log.info("Skill tooltip diagnostic: id={}.{} parent={} type={} "
                            + "name=<{}> text=<{}> targetVerb=<{}> actions={} "
                            + "bounds={} containsMouse={} hidden={} selfHidden={}",
                    widget.getId() >>> 16, widget.getId() & 0xFFFF,
                    widget.getParentId(), widget.getType(),
                    name, text, targetVerb,
                    actions == null ? "[]" : String.join("|", actions),
                    bounds, containsMouse,
                    widget.isHidden(), widget.isSelfHidden());
        }

        if (!candidates.isEmpty())
        {
            log.info("Skill tooltip diagnostic: logged {} valid nearby widgets; {} candidates total",
                    limit, candidates.size());
        }
    }

    private static int area(Rectangle bounds)
    {
        return bounds.width * bounds.height;
    }

    private static void addChildren(Deque<Widget> queue, Widget[] children)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            if (child != null)
            {
                queue.addLast(child);
            }
        }
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}