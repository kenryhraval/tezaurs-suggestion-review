import type { SaveSenseDraft, TezaursEntryDetails, TezaursSenseDetails } from '../api/types'
import { buildDraftGrammarData } from './grammar'

type DisplayedSense = {
  key: string
  parentKey: string | null
  draftIndex: number | null
  sense: TezaursSenseDetails
  isNew: boolean
}

export function buildDisplayedSenses(existingEntry: TezaursEntryDetails | null, draftSenses: SaveSenseDraft[]) {
  const existingSenses = existingEntry?.senses ?? []
  const displayedSenses: DisplayedSense[] = []

  for (const sense of existingSenses) {
    const editedSenseDraft = draftSenses.find((draftSense) => draftSense.id && draftSense.sourceSenseId === sense.id)
    if (editedSenseDraft) {
      continue
    }

    let parentKey: string | null = null
    if (sense.parentSenseId !== null) {
      const parentSenseDraft = draftSenses.find((draftSense) => draftSense.id && draftSense.sourceSenseId === sense.parentSenseId)
      if (parentSenseDraft) {
        parentKey = `draft:${parentSenseDraft.id}`
      } else {
        parentKey = `tezaurs:${sense.parentSenseId}`
      }
    }
    displayedSenses.push({
      key: `tezaurs:${sense.id}`,
      parentKey,
      draftIndex: null,
      sense,
      isNew: false,
    })
  }

  draftSenses.forEach((draftSense, index) => {
    if (!draftSense.id) {
      return
    }

    const originalSense = existingSenses.find((sense) => sense.id === draftSense.sourceSenseId)
    let parentKey = draftSense.parentDraftSenseId ? `draft:${draftSense.parentDraftSenseId}` : null
    if (draftSense.parentTezaursSenseId) {
      const parentSenseDraft = draftSenses.find((sense) => sense.sourceSenseId === draftSense.parentTezaursSenseId)
      if (parentSenseDraft) {
        parentKey = `draft:${parentSenseDraft.id}`
      } else {
        parentKey = `tezaurs:${draftSense.parentTezaursSenseId}`
      }
    }

    displayedSenses.push({
      key: `draft:${draftSense.id}`,
      parentKey,
      draftIndex: index,
      isNew: existingEntry !== null && !draftSense.sourceSenseId,
      sense: {
        id: draftSense.sourceSenseId ?? index,
        parentSenseId: null,
        orderNo: index,
        senseTag: null,
        gloss: draftSense.gloss,
        hidden: false,
        examples: originalSense?.examples ?? [],
        data: buildDraftGrammarData(originalSense?.data, draftSense.flags, draftSense.structuralRestrictions),
      },
    })
  })

  const existingSensePositions = new Map(existingSenses.map((sense, index) => [sense.id, index]))
  displayedSenses.sort((left, right) => {
    if (left.isNew !== right.isNew) {
      return left.isNew ? 1 : -1
    }
    if (left.isNew) {
      return 0
    }

    const leftSensePosition = existingSensePositions.get(left.sense.id) ?? 0
    const rightSensePosition = existingSensePositions.get(right.sense.id) ?? 0
    return leftSensePosition - rightSensePosition
  })

  for (const sense of displayedSenses) {
    const parentIsVisible = displayedSenses.some((parent) => parent.key === sense.parentKey)
    if (sense.parentKey && !parentIsVisible) {
      sense.parentKey = null
    }
  }
  return displayedSenses
}
