import { useEffect, useState } from 'react'
import { getModelPrompt } from '../../api/client'
import type { ModelOperation } from '../../api/types'

type Props = {
  operation: ModelOperation
  additionalInstructions: string
  disabled: boolean
  onChange: (instructions: string) => void
}

export function ModelPromptEditor({ operation, additionalInstructions, disabled, onChange }: Props) {
  const [prompt, setPrompt] = useState<string | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let requestIsOutdated = false
    async function loadPrompt() {
      try {
        const loaded = await getModelPrompt(operation)
        if (!requestIsOutdated) setPrompt(loaded)
      } catch (cause) {
        if (!requestIsOutdated) setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt uzvedni.')
      }
    }
    loadPrompt()
    return () => { requestIsOutdated = true }
  }, [operation])

  return <div className="model-prompt-editor">
    <details>
      <summary>Uzvedne</summary>
      {error && <p className="error" role="alert">{error}</p>}
      {!prompt && !error && <p className="muted">Ielādē uzvedni...</p>}
      {prompt && <p className="model-prompt-text">{prompt}</p>}
    </details>
    <label>
      Papildu norādījumi
      <textarea className="form-control form-control-sm" rows={3} maxLength={4000} value={additionalInstructions}
        disabled={disabled} onChange={(event) => onChange(event.target.value)} />
    </label>
  </div>
}
