/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.api.sponge;

import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import me.lucko.luckperms.api.LocalizedNode;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.groups.Group;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.permission.NodeTree;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectCollection;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.util.Tristate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static me.lucko.luckperms.utils.ArgumentChecker.unescapeCharacters;

@EqualsAndHashCode(of = "group")
public class LuckPermsGroupSubject implements Subject {
    public static LuckPermsGroupSubject wrapGroup(Group group, LuckPermsService service) {
        return new LuckPermsGroupSubject(group, service);
    }

    @Getter
    private Group group;

    private LuckPermsService service;

    @Getter
    private LuckPermsSubjectData subjectData;

    @Getter
    private LuckPermsSubjectData transientSubjectData;

    private LuckPermsGroupSubject(Group group, LuckPermsService service) {
        this.group = group;
        this.subjectData = new LuckPermsSubjectData(true, service, group);
        this.transientSubjectData = new LuckPermsSubjectData(false, service, group);
        this.service = service;
    }

    @Override
    public String getIdentifier() {
        return group.getObjectName();
    }

    @Override
    public Optional<CommandSource> getCommandSource() {
        return Optional.empty();
    }

    @Override
    public SubjectCollection getContainingCollection() {
        return service.getGroupSubjects();
    }

    @Override
    public boolean hasPermission(@NonNull Set<Context> contexts, @NonNull String node) {
        return getPermissionValue(contexts, node).asBoolean();
    }

    @Override
    public Tristate getPermissionValue(@NonNull Set<Context> contexts, @NonNull String node) {
        Map<String, Boolean> permissions = group.getAllNodesFiltered(service.calculateContexts(contexts)).stream()
                .map(LocalizedNode::getNode)
                .collect(Collectors.toMap(Node::getPermission, Node::getValue));

        Tristate t = NodeTree.of(permissions).get(node);
        if (t != Tristate.UNDEFINED) {
            return t;
        }

        t = service.getGroupSubjects().getDefaults().getPermissionValue(contexts, node);
        if (t != Tristate.UNDEFINED) {
            return t;
        }

        t = service.getDefaults().getPermissionValue(contexts, node);
        return t;
    }

    @Override
    public boolean isChildOf(@NonNull Set<Context> contexts, @NonNull Subject parent) {
        return parent instanceof LuckPermsGroupSubject && getPermissionValue(contexts, "group." + parent.getIdentifier()).asBoolean();
    }

    @Override
    public List<Subject> getParents(@NonNull Set<Context> contexts) {
        List<Subject> subjects = group.getAllNodesFiltered(service.calculateContexts(contexts)).stream()
                .map(LocalizedNode::getNode)
                .filter(Node::isGroupNode)
                .map(Node::getGroupName)
                .map(s -> service.getGroupSubjects().get(s))
                .collect(Collectors.toList());

        subjects.addAll(service.getGroupSubjects().getDefaults().getParents(contexts));
        subjects.addAll(service.getDefaults().getParents(contexts));

        return ImmutableList.copyOf(subjects);
    }

    @Override
    public Optional<String> getOption(Set<Context> set, String s) {
        Optional<String> option;
        if (s.equalsIgnoreCase("prefix")) {
            option = getChatMeta(set, true);

        } else if (s.equalsIgnoreCase("suffix")) {
            option = getChatMeta(set, false);

        } else {
            option = getMeta(set, s);
        }

        if (option.isPresent()) {
            return option;
        }

        option = service.getGroupSubjects().getDefaults().getOption(set, s);
        if (option.isPresent()) {
            return option;
        }

        return service.getDefaults().getOption(set, s);
    }

    @Override
    public Set<Context> getActiveContexts() {
        return SubjectData.GLOBAL_CONTEXT;
    }

    private Optional<String> getChatMeta(Set<Context> contexts, boolean prefix) {
        int priority = Integer.MIN_VALUE;
        String meta = null;

        for (Node n : group.getAllNodesFiltered(service.calculateContexts(contexts))) {
            if (!n.getValue()) {
                continue;
            }

            if (prefix ? !n.isPrefix() : !n.isSuffix()) {
                continue;
            }

            Map.Entry<Integer, String> value = prefix ? n.getPrefix() : n.getSuffix();
            if (value.getKey() > priority) {
                meta = value.getValue();
                priority = value.getKey();
            }
        }

        return meta == null ? Optional.empty() : Optional.of(unescapeCharacters(meta));
    }

    private Optional<String> getMeta(Set<Context> contexts, String key) {
        for (Node n : group.getAllNodesFiltered(service.calculateContexts(contexts))) {
            if (!n.getValue()) {
                continue;
            }

            if (!n.isMeta()) {
                continue;
            }

            Map.Entry<String, String> m = n.getMeta();
            if (!m.getKey().equalsIgnoreCase(key)) {
                continue;
            }

            return Optional.of(m.getValue());
        }

        return Optional.empty();
    }
}