from verity_sdk.utils import pack_message_for_verity, uuid
from verity_sdk.transports import send_packed_message
from verity_sdk.utils.MessageFamily import MessageFamily


class Protocol(MessageFamily):

    # Messages
    STATUS = 'status-report'
    PROBLEM_REPORT = 'problem-report'

    def __init__(self, msg_family, msg_family_version, msg_qualifier=None, thread_id=None):
        super().__init__(msg_family, msg_family_version, msg_qualifier)
        if thread_id is not None:
            self.thread_id = thread_id
        else:
            self.thread_id = uuid()

    def _add_thread(self, msg):
        msg['~thread'] = {
            'thid': self.thread_id
        }

    #pylint:disable=no-self-use
    def _add_relationship(self, msg, for_relationship):
        msg['~for_relationship'] = for_relationship

    async def get_message_bytes(self, context, message):
        return await pack_message_for_verity(context, message)

    async def send_message(self, context, message):
        await send_packed_message(context, message)
