package ru.misis.model

import ru.misis.event.Event

object Account {

    var stateList: List[State] = List()

    case class State(id: Int, amount: Int) {
        def update(value: Int): State = {
            val updatedState = copy(amount = amount + value)
            stateList = stateList.map(state =>
                if (state.id == id) updatedState else state)
            updatedState
        }
    }

    def create(): State = {
        val newId = stateList.size + 1
        val newState = State(newId, 0)
        stateList = newState :: stateList
        newState
    }

    case class AccountUpdated(accountId: Int, value: Int, category: Option[String] = None, needCommit: Option[Boolean] = Some(false)) extends Event

}