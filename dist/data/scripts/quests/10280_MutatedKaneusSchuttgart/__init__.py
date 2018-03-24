# 2010-06-29 by Gnacik
# Based on official server Franz and Rpg

from l2server.gameserver.model.quest import State
from l2server.gameserver.model.quest.jython import QuestJython as JQuest
from l2server.util import Rnd

qn = "10280_MutatedKaneusSchuttgart"

# NPCs
VISHOTSKY = 31981
ATRAXIA = 31972
VENOMOUS_STORACE = 18571
KEL_BILETTE = 18573

# Items
TISSUE_VS = 13838
TISSUE_KB = 13839


class Quest(JQuest):
    def __init__(self, id, name, descr):
        JQuest.__init__(self, id, name, descr)
        self.questItemIds = [TISSUE_VS, TISSUE_KB]

    def onAdvEvent(self, event, npc, player):
        htmltext = event
        st = player.getQuestState(qn)
        if not st: return

        if event == "31981-03.htm":
            st.setState(State.STARTED)
            st.set("cond", "1")
            st.playSound("ItemSound.quest_accept")
        elif event == "31972-03.htm":
            st.unset("cond")
            st.rewardItems(57, 210000)
            st.exitQuest(False)
            st.playSound("ItemSound.quest_finish")
        return htmltext

    def onTalk(self, npc, player):
        htmltext = Quest.getNoQuestMsg(player)
        st = player.getQuestState(qn)
        if not st: return htmltext

        npcId = npc.getNpcId()
        cond = st.getInt("cond")

        if npcId == VISHOTSKY:
            if st.getState() == State.COMPLETED:
                htmltext = "31981-06.htm"
            elif st.getState() == State.CREATED and player.getLevel() >= 58:
                htmltext = "31981-01.htm"
            elif st.getState() == State.CREATED and player.getLevel() < 58:
                htmltext = "31981-00.htm"
            elif st.getQuestItemsCount(TISSUE_VS) > 0 and st.getQuestItemsCount(TISSUE_KB) > 0:
                htmltext = "31981-05.htm"
            elif cond == 1:
                htmltext = "31981-04.htm"
        elif npcId == ATRAXIA:
            if st.getState() == State.COMPLETED:
                htmltext = Quest.getAlreadyCompletedMsg(player)
            elif st.getQuestItemsCount(TISSUE_VS) > 0 and st.getQuestItemsCount(TISSUE_KB) > 0:
                htmltext = "31972-02.htm"
            else:
                htmltext = "31972-01.htm"
        return htmltext

    def onKill(self, npc, player, isPet):
        npcId = npc.getNpcId()
        party = player.getParty()
        if party:
            PartyMembers = []
            for member in party.getPartyMembers().toArray():
                st = member.getQuestState(qn)
                if st and st.getState() == State.STARTED and st.getInt("cond") == 1:
                    if npcId == VENOMOUS_STORACE and st.getQuestItemsCount(TISSUE_VS) == 0:
                        PartyMembers.append(st)
                    elif npcId == TISSUE_KB and st.getQuestItemsCount(TISSUE_KB) == 0:
                        PartyMembers.append(st)
            if len(PartyMembers) == 0: return
            winnerst = PartyMembers[Rnd.get(len(PartyMembers))]
            if npcId == VENOMOUS_STORACE and winnerst.getQuestItemsCount(TISSUE_VS) == 0:
                winnerst.giveItems(TISSUE_VS, 1)
                winnerst.playSound("ItemSound.quest_itemget")
            elif npcId == KEL_BILETTE and winnerst.getQuestItemsCount(TISSUE_KB) == 0:
                winnerst.giveItems(TISSUE_KB, 1)
                winnerst.playSound("ItemSound.quest_itemget")
        else:
            st = player.getQuestState(qn)
            if not st: return
            if st.getState() != State.STARTED: return

            if npcId == VENOMOUS_STORACE and st.getQuestItemsCount(TISSUE_VS) == 0:
                st.giveItems(TISSUE_VS, 1)
                st.playSound("ItemSound.quest_itemget")
            elif npcId == KEL_BILETTE and st.getQuestItemsCount(TISSUE_KB) == 0:
                st.giveItems(TISSUE_KB, 1)
                st.playSound("ItemSound.quest_itemget")
        return


QUEST = Quest(10280, qn, "Mutated Kaneus - Schuttgart")

QUEST.addStartNpc(VISHOTSKY)
QUEST.addTalkId(VISHOTSKY)
QUEST.addTalkId(ATRAXIA)

QUEST.addKillId(VENOMOUS_STORACE)
QUEST.addKillId(KEL_BILETTE)
