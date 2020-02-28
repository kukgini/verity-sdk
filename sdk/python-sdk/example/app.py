__copyright__ = "COPYRIGHT 2013-2019, ALL RIGHTS RESERVED, EVERNYM INC."

import asyncio
import json
import logging
import sys
import traceback

import pyqrcode

from aiohttp import web
from aiohttp.web_routedef import RouteTableDef
from asyncio.base_events import Server

from example.helper import console_input, console_yes_no, print_error, get_random_version

from src.handlers import Handlers
from src.protocols.Connecting import Connecting
from src.protocols.IssueCredential import IssueCredential
from src.protocols.IssuerSetup import IssuerSetup
from src.protocols.PresentProof import PresentProof
from src.protocols.Provision import Provision
from src.protocols.UpdateEndpoint import UpdateEndpoint
from src.protocols.WriteCredentialDefinition import WriteCredentialDefinition
from src.protocols.WriteSchema import WriteSchema
from src.utils.Context import Context
from src.utils import truncate_invite_details, uuid
from src.utils.Did import Did, create_new_did

DEFAULT_VERITY_URL = "http://localhost:9000"

context: Context
issuer_did: str = ''
issuer_verkey: str = ''

server: Server
port: int = 4000
handlers: Handlers = Handlers()
routes: RouteTableDef = web.RouteTableDef()

async def example():
    global for_did

    logging.info("Starting setup")
    await setup() # TODO @devin: Does not check for existing config yet because the config isn't being written

    for_did = await create_connection()

    #await ask_question(for_did)

    schema_id = await write_ledger_schema()
    cred_def_id = await write_ledger_cred_def(schema_id)

    await issue_credential(for_did, cred_def_id)

    await request_proof(for_did)


def default_handler(_, message):
    print("Message not handled!")
    print(message)


def get_config(file_path) -> str:
    try:
        with open(file_path, 'r') as f:
            if console_yes_no(f"Reuse Verity Context (in {file_path})"):
                return f.read()
    except FileNotFoundError:
        pass
    return ""

async def provision_agent() -> str:
    global context
    wallet_name = uuid()
    wallet_key = '12345'
    wallet_path = None

    verity_url = get_verity_url()

    # Create Context
    context = await Context.create(wallet_name, wallet_key, verity_url, '')

    # Create and store sdk pairwise DID/Verkey
    sdk_pairwise_did_verkey: Did = await create_new_did(context.wallet_handle)
    # Add sdk did/verkey to Context
    context.sdk_pairwise_did = sdk_pairwise_did_verkey.did
    context.sdk_pairwise_verkey = sdk_pairwise_did_verkey.verkey

    context = await Provision().provision_sdk(context)
    return context


def get_verity_url():
    url = console_input(f"Verity Application Endpoint [{DEFAULT_VERITY_URL}]").strip()
    if not url:
        url = DEFAULT_VERITY_URL
    return url


async def update_webhook_endpoint():
    global context, port
    webhook_from_ctx: str = context.endpoint_url

    if not webhook_from_ctx:
        # Default to localhost on the default port
        webhook_from_ctx = f"http://localhost:{port}"

    webhook: str = console_input(f"Ngrok endpoint [{webhook_from_ctx}]")

    if not webhook:
        webhook = webhook_from_ctx

    print(f"Using Webhook: {webhook}")
    context.endpoint_url = webhook
    # The SDK lets Verity know what its endpoint is
    await UpdateEndpoint(context).update()


async def create_connection():
    global context
    global handlers

    connecting: Connecting = Connecting(include_public_did=True)

    first_step = loop.create_future()

    async def invite_detail_handler(msg_name, message):
        if msg_name == Connecting.INVITE_DETAIL:
            invite_detail = message['inviteDetail']
            for_did = invite_detail['senderDetail']['DID']
            truncated_invite_detail = truncate_invite_details(invite_detail)
            print(truncated_invite_detail)

            # write QRCode to disk
            qr = pyqrcode.create(json.dumps(truncated_invite_detail))
            qr.png('qrcode.png')

            print()
            print('QR code at: qrcode.png')

            first_step.set_result(for_did)
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)

    handlers.add_handler(Connecting.MSG_FAMILY, Connecting.MSG_FAMILY_VERSION, invite_detail_handler)

    await connecting.connect(context)
    for_did = await first_step

    second_step = loop.create_future()

    async def connection_accepted_handler(msg_name, message):
        print_message(msg_name, message)
        if msg_name == Connecting.CONN_REQ_ACCEPTED:
            second_step.set_result(None)
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)

    handlers.add_handler(Connecting.MSG_FAMILY, Connecting.MSG_FAMILY_VERSION, connection_accepted_handler)  # Note this overrides the handler for this message family! This is for demonstration purposes only.

    await second_step
    return for_did

async def write_ledger_schema() -> str:
    schema_name = 'Diploma'
    schema_version = get_random_version()
    schema_attrs = ['name', 'degree']

    schema = WriteSchema(schema_name, schema_version, schema_attrs)

    first_step = loop.create_future()

    async def schema_written_handler(msg_name, message):
        print_message(msg_name, message)
        if msg_name == WriteSchema.STATUS:
            first_step.set_result(message['schemaId'])
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)
    handlers.add_handler(WriteSchema.MSG_FAMILY, WriteSchema.MSG_FAMILY_VERSION, schema_written_handler)

    await schema.write(context)
    schema_id = await first_step
    return schema_id

async def write_ledger_cred_def(schema_id: str) -> str:
    cred_def_name = 'Trinity College Diplomas'
    cred_def_tag = 'latest'

    cred_def = WriteCredentialDefinition(cred_def_name, schema_id, cred_def_tag)

    first_step = loop.create_future()

    async def cred_def_written_handler(msg_name, message):
        print_message(msg_name, message)
        if msg_name == WriteCredentialDefinition.STATUS:
            first_step.set_result(message['credDefId'])
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)
    handlers.add_handler(WriteCredentialDefinition.MSG_FAMILY, WriteCredentialDefinition.MSG_FAMILY_VERSION, cred_def_written_handler)

    await cred_def.write(context)
    cred_def_id = await first_step
    return cred_def_id


async def issue_credential(for_did, cred_def_id):
    credential_name = 'Degree'
    credential_data = {
        'name': 'Joe Smith',
        'degree': 'Bachelors'
    }

    issue = IssueCredential(for_did, None, credential_name, cred_def_id, credential_data)

    first_step = loop.create_future()

    async def cred_offer_accepted_handler(msg_name, message):
        print_message(msg_name, message)
        if msg_name == IssueCredential.ASK_ACCEPT:
            first_step.set_result(None)
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)
    handlers.add_handler(IssueCredential.MSG_FAMILY, IssueCredential.MSG_FAMILY_VERSION, cred_offer_accepted_handler)

    await issue.offer_credential(context)
    await first_step
    await issue.issue_credential(context)
    await asyncio.sleep(3) # Wait a few seconds for the credential to arrive before sending the proof

async def request_proof(for_did):
    global issuer_did

    proof_name = 'Proof of Degree'
    proof_attrs = [
        {
          'name': 'name',
          'restrictions': [{ issuer_did: issuer_did }]
        },
        {
          'name': 'degree',
          'restrictions': [{ issuer_did: issuer_did }]
        }
    ]

    proof = PresentProof(for_did, None, proof_name, proof_attrs)

    first_step = loop.create_future()

    async def proof_handler(msg_name, message):
        print_message(msg_name, message)
        if msg_name == PresentProof.PROOF_RESULT:
            first_step.set_result(None) # proof data contained inside `message`
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)
    handlers.add_handler(PresentProof.MSG_FAMILY, PresentProof.MSG_FAMILY_VERSION, proof_handler)

    await proof.request(context)
    await first_step


async def setup():
    global context
    global issuer_did

    config = get_config("verity-context.json")
    if config:
        context = await Context.create_with_config(config)
    else:
        context = await provision_agent()
    await update_webhook_endpoint()
    print("endpoint updated")
    handlers.set_default_handler(default_handler)

    await issuer_identifier()

    if not issuer_did:
        await setup_issuer()

async def issuer_identifier():
    issuer_setup = IssuerSetup()

    first_step = loop.create_future()

    async def public_identifier_handler(msg_name, message):
        global issuer_did
        global issuer_verkey

        if msg_name == IssuerSetup.PUBLIC_IDENTIFIER:
            issuer_did = message['did']
            issuer_verkey = message['verKey']
            first_step.set_result(None)
        elif msg_name == IssuerSetup.PROBLEM_REPORT:
            # Do nothing. Just means we need to write the keys to the ledger.
            first_step.set_result(None)
        else:
            non_handled(f"Message name is not handled - {msg_name}", message)
    handlers.add_handler(IssuerSetup.MSG_FAMILY, IssuerSetup.MSG_FAMILY_VERSION, public_identifier_handler)

    await issuer_setup.current_public_identifier(context)
    await asyncio.sleep(2)
    await first_step

async def setup_issuer():
    issuer_setup = IssuerSetup()

    first_step = loop.create_future()

    async def public_identifier_handler(msg_name, message):
        global issuer_did
        global issuer_verkey

        if msg_name == IssuerSetup.PUBLIC_IDENTIFIER_CREATED:
            issuer_did = message['identifier']['did']
            issuer_verkey = message['identifier']['verKey']
            print('The issuer DID and Verkey must be on the ledger.')
            print(f'Please add DID ({issuer_did}) and Verkey ({issuer_verkey}) to ledger.')
            console_input('Press ENTER when DID is on ledger')
            first_step.set_result(None)
        else:
            non_handled(f"Message name is not handled - {msg_name}")

    handlers.add_handler(IssuerSetup.MSG_FAMILY, IssuerSetup.MSG_FAMILY_VERSION, public_identifier_handler)

    await issuer_setup.create(context)
    await first_step


def non_handled(msg: str, message=None):
    global server
    print_error(msg)
    if message is not None:
        print_error(message)
    server.close()
    sys.exit(1)


def print_message(msg_name, message):
    print(f"{msg_name} - {message}")


@routes.post('/')
async def endpoint_handler(request):
    try:
        await handlers.handle_message(context, await request.read())
        return web.Response(text="Success")
    except Exception as e:
        traceback.print_exc()
        return web.Response(text=str(e))


async def main(loop):
    global port
    global server
    app = web.Application(loop=loop)
    app.add_routes(routes)
    server = await loop.create_server(app.make_handler(), '0.0.0.0', port)
    print("Listening on port {}".format(port))
    await loop.create_task(example())


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main(loop))
    loop.run_forever()
