export function casePath(publicId: string) {
  return '/' + publicId
}

export function parsePublicId(value: string | undefined): string | null {
  if (value === undefined) {
    return null
  }

  const publicId = value.toLowerCase()
  if (publicId.length < 6) {
    return null
  }

  const allowedCharacters = /^[a-z0-9]+$/
  if (!allowedCharacters.test(publicId)) {
    return null
  }

  return publicId
}
