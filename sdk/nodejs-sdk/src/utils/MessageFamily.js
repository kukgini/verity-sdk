'use strict'
const { v4: uuidv4 } = require('uuid')
const utils = require('./index')

module.exports = class MessageFamily {
  constructor (msgFamily, msgFamilyVersion, msgQualifier = utils.constants.EVERNYM_MSG_QUALIFIER) {
    this.msgFamily = msgFamily
    this.msgFamilyVersion = msgFamilyVersion
    this.msgQualifier = msgQualifier
  }

  _getNewId () {
    return uuidv4()
  }

  _getBaseMessage (msgName) {
    const msg = {
      '@id': this._getNewId(),
      '@type': this._getMessageType(msgName)
    }
    return msg
  }

  _getMessageType (messageName) {
    return `${this.msgQualifier};spec/${this.msgFamily}/${this.msgFamilyVersion}/${messageName}`
  }

  static parseMessageType (messageType) {
    const result = {}
    const msgTypeParts1 = messageType.split(';spec/')
    result.qualifier = msgTypeParts1[0]
    const msgTypeParts2 = msgTypeParts1[1].split('/')
    result.msgFamily = msgTypeParts2[0]
    result.msgFamilyVersion = msgTypeParts2[1]
    result.msgName = msgTypeParts2[2]
    return result
  }
}
