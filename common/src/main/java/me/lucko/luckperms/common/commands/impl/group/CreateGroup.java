/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
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

package me.lucko.luckperms.common.commands.impl.group;

import me.lucko.luckperms.api.event.cause.CreationCause;
import me.lucko.luckperms.common.commands.CommandResult;
import me.lucko.luckperms.common.commands.abstraction.SingleCommand;
import me.lucko.luckperms.common.commands.sender.Sender;
import me.lucko.luckperms.common.constants.DataConstraints;
import me.lucko.luckperms.common.constants.Permission;
import me.lucko.luckperms.common.data.LogEntry;
import me.lucko.luckperms.common.locale.CommandSpec;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.utils.Predicates;

import java.util.List;

public class CreateGroup extends SingleCommand {
    public CreateGroup(LocaleManager locale) {
        super(CommandSpec.CREATE_GROUP.spec(locale), "CreateGroup", Permission.CREATE_GROUP, Predicates.not(1));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, List<String> args, String label) {
        if (args.size() == 0) {
            sendUsage(sender, label);
            return CommandResult.INVALID_ARGS;
        }

        String groupName = args.get(0).toLowerCase();
        if (!DataConstraints.GROUP_NAME_TEST.test(groupName)) {
            Message.GROUP_INVALID_ENTRY.send(sender);
            return CommandResult.INVALID_ARGS;
        }

        if (plugin.getStorage().loadGroup(groupName).join()) {
            Message.GROUP_ALREADY_EXISTS.send(sender);
            return CommandResult.INVALID_ARGS;
        }

        if (!plugin.getStorage().createAndLoadGroup(groupName, CreationCause.COMMAND).join()) {
            Message.CREATE_GROUP_ERROR.send(sender);
            return CommandResult.FAILURE;
        }

        Message.CREATE_SUCCESS.send(sender, groupName);
        LogEntry.build().actor(sender).actedName(groupName).type('G').action("create").build().submit(plugin, sender);
        return CommandResult.SUCCESS;
    }
}
