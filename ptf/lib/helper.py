# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
#
import re

import google.protobuf.text_format
import grpc
from ptf import testutils as testutils
from p4.config.v1 import p4info_pb2
from p4.v1 import p4runtime_pb2, p4runtime_pb2_grpc, p4data_pb2

from convert import encode


def get_match_field_value(match_field):
    match_type = match_field.WhichOneof("field_match_type")
    if match_type == 'valid':
        return match_field.valid.value
    elif match_type == 'exact':
        return match_field.exact.value
    elif match_type == 'lpm':
        return match_field.lpm.value, match_field.lpm.prefix_len
    elif match_type == 'ternary':
        return match_field.ternary.value, match_field.ternary.mask
    elif match_type == 'range':
        return match_field.range.low, match_field.range.high
    else:
        raise Exception("Unsupported match type with type %r" % match_type)


class P4InfoHelper(object):

    def __init__(self, p4info):
        self.p4info = p4info

        self.next_mbr_id = 1
        self.next_grp_id = 1

    def get_next_mbr_id(self):
        mbr_id = self.next_mbr_id
        self.next_mbr_id = self.next_mbr_id + 1
        return mbr_id

    def read_pkt_count(self, c_name, line_id):
        counter = self.read_counter(c_name, line_id, typ="BOTH")
        return counter.data.packet_count

    def read_byte_count(self, c_name, line_id):
        counter = self.read_counter(c_name, line_id, typ="BYTES")
        return counter.data.byte_count

    def read_counter(self, c_name, c_index, typ):
        # Check counter type with P4Info
        counter = self.get_obj('counters', c_name)
        counter_type_unit = p4info_pb2.CounterSpec.Unit.items()[counter.spec.unit][0]
        if counter_type_unit != "BOTH" and counter_type_unit != typ:
            raise Exception("Counter " + c_name + " is of type " + counter_type_unit +
                            ", but requested: " + typ)
        req = self.get_new_read_request()
        entity = req.entities.add()
        counter_entry = entity.counter_entry
        c_id = self.get_id('counters', c_name)
        counter_entry.counter_id = c_id
        index = counter_entry.index
        index.index = c_index

        for entity in self.read_request(req):
            if entity.HasField("counter_entry"):
                return entity.counter_entry
        return None

    def clear_counters(self):
        pass

    def read_request(self, req):
        entities = []
        grpc_addr = testutils.test_param_get("grpcaddr")
        channel = grpc.insecure_channel(grpc_addr)
        stub = p4runtime_pb2_grpc.P4RuntimeStub(channel)
        try:
            for resp in stub.Read(req):
                entities.extend(resp.entities)
        except grpc.RpcError as e:
            if e.code() != grpc.StatusCode.UNKNOWN:
                raise e
            raise P4RuntimeException(e)
        return entities

    def write_request(self, req, store=True):
        rep = self._write(req)
        if store:
            self.reqs.append(req)
        return rep

    def get_new_write_request(self):
        req = p4runtime_pb2.WriteRequest()
        req.device_id = int(testutils.test_param_get("device_id"))
        election_id = req.election_id
        election_id.high = 0
        election_id.low = self.election_id
        return req

    def get_new_read_request(self):
        req = p4runtime_pb2.ReadRequest()
        req.device_id = int(testutils.test_param_get("device_id"))
        return req

    def get_next_grp_id(self):
        grp_id = self.next_grp_id
        self.next_grp_id = self.next_grp_id + 1
        return grp_id

    def get_enum_member_val(self, enum_name, enum_member):
        members = self.get_enum_members(name=enum_name)
        val = members.get(enum_member, None)
        if val is None:
            raise Exception("%s not a member of enum %s. Available Members: %s" \
                            % (enum_member, enum_name, str(list(members.keys()))))
        return val

    def get_enum_obj(self, name):
        if "type_info" in dir(self.p4info):
            type_info = self.p4info.type_info
            if "serializable_enums" in dir(type_info):
                for key, val in type_info.serializable_enums.items():
                    if key == name:
                        return val
        raise AttributeError("Could not find enum named %s" % name)

    def get_enum_members(self, name=None, obj=None):
        if obj is None:
            if name is None:
                raise AssertionError("Must provide either an enum name or enum object")
            obj = self.get_enum_obj(name)
        return {member.name: member.value for member in obj.members}

    def get_enum_width(self, name):
        return self.get_enum_obj(name).underlying_type.bitwidth

    def get(self, entity_type, name=None, id=None):
        if name is not None and id is not None:
            raise AssertionError("name or id must be None")

        for o in getattr(self.p4info, entity_type):
            pre = o.preamble
            if name:
                if pre.name == name:
                    return o
            else:
                if pre.id == id:
                    return o

        if name:
            raise AttributeError("Could not find %r of type %s" % (name, entity_type))
        else:
            raise AttributeError("Could not find id %r of type %s" % (id, entity_type))

    def get_id(self, entity_type, name):
        return self.get(entity_type, name=name).preamble.id

    def get_name(self, entity_type, id):
        return self.get(entity_type, id=id).preamble.name

    def get_obj(self, entity_type, name):
        return self.get(entity_type, name=name)

    def __getattr__(self, attr):
        # Synthesize convenience functions for name to id lookups for top-level
        # entities e.g. get_tables_id(name_string) or
        # get_actions_id(name_string)
        m = re.search(r"^get_(\w+)_id$", attr)
        if m:
            primitive = m.group(1)
            return lambda name: self.get_id(primitive, name)

        # Synthesize convenience functions for id to name lookups
        # e.g. get_tables_name(id) or get_actions_name(id)
        m = re.search(r"^get_(\w+)_name$", attr)
        if m:
            primitive = m.group(1)
            return lambda x: self.get_name(primitive, x)

        raise AttributeError("%r object has no attribute %r (check your P4Info)" %
                             (self.__class__, attr))

    def get_match_field(self, table_name, name=None, id=None):
        t = None
        for t in self.p4info.tables:
            if t.preamble.name == table_name:
                break
        if not t:
            raise AttributeError("No such table %r in P4Info" % table_name)
        for mf in t.match_fields:
            if name is not None:
                if mf.name == name:
                    return mf
            elif id is not None:
                if mf.id == id:
                    return mf
        raise AttributeError("%r has no match field %r (check your P4Info)" %
                             (table_name, name if name is not None else id))

    def get_packet_metadata(self, meta_type, name=None, id=None):
        for t in self.p4info.controller_packet_metadata:
            pre = t.preamble
            if pre.name == meta_type:
                for m in t.metadata:
                    if name is not None:
                        if m.name == name:
                            return m
                    elif id is not None:
                        if m.id == id:
                            return m
        raise AttributeError("ControllerPacketMetadata %r has no metadata %r (check your P4Info)" %
                             (meta_type, name if name is not None else id))

    def get_match_field_id(self, table_name, match_field_name):
        return self.get_match_field(table_name, name=match_field_name).id

    def get_match_field_name(self, table_name, match_field_id):
        return self.get_match_field(table_name, id=match_field_id).name

    def get_match_field_pb(self, table_name, match_field_name, value):
        p4info_match = self.get_match_field(table_name, match_field_name)
        bitwidth = p4info_match.bitwidth
        p4runtime_match = p4runtime_pb2.FieldMatch()
        p4runtime_match.field_id = p4info_match.id
        match_type = p4info_match.match_type
        if match_type == p4info_pb2.MatchField.EXACT:
            exact = p4runtime_match.exact
            exact.value = encode(value, bitwidth)
        elif match_type == p4info_pb2.MatchField.LPM:
            if type(value) is str and '/' in value:
                value = value.split('/')
                value[1] = int(value[1])
            lpm = p4runtime_match.lpm
            lpm.value = encode(value[0], bitwidth)
            lpm.prefix_len = value[1]
        elif match_type == p4info_pb2.MatchField.TERNARY:
            lpm = p4runtime_match.ternary
            lpm.value = encode(value[0], bitwidth)
            lpm.mask = encode(value[1], bitwidth)
        elif match_type == p4info_pb2.MatchField.RANGE:
            lpm = p4runtime_match.range
            lpm.low = encode(value[0], bitwidth)
            lpm.high = encode(value[1], bitwidth)
        else:
            raise Exception("Unsupported match type with type %r" % match_type)
        return p4runtime_match

    def get_action_param(self, action_name, name=None, id=None):
        for a in self.p4info.actions:
            pre = a.preamble
            if pre.name == action_name:
                for p in a.params:
                    if name is not None:
                        if p.name == name:
                            return p
                    elif id is not None:
                        if p.id == id:
                            return p
        raise AttributeError("Action %r has no param %r (check your P4Info)" %
                             (action_name, name if name is not None else id))

    def get_counter(self, counter_name):
        for a in self.p4info.direct_counters:
            pre = a.preamble
            if pre.name == counter_name:
                return a
        raise AttributeError("Counter %r doesnt exist (check your P4Info)" % (counter_name))

    def get_action_param_id(self, action_name, param_name):
        return self.get_action_param(action_name, name=param_name).id

    def get_action_param_name(self, action_name, param_id):
        return self.get_action_param(action_name, id=param_id).name

    def get_action_param_pb(self, action_name, param_name, value):
        p4info_param = self.get_action_param(action_name, param_name)
        p4runtime_param = p4runtime_pb2.Action.Param()
        p4runtime_param.param_id = p4info_param.id
        p4runtime_param.value = encode(value, p4info_param.bitwidth)
        return p4runtime_param

    def build_table_entry(self, table_name, match_fields=None, default_action=False,
                          action_name=None, action_params=None, group_id=None, priority=None):
        table_entry = p4runtime_pb2.TableEntry()
        table_entry.table_id = self.get_tables_id(table_name)

        if priority is not None:
            table_entry.priority = priority

        if match_fields:
            table_entry.match.extend([
                self.get_match_field_pb(table_name, match_field_name, value)
                for match_field_name, value in match_fields.items()
            ])

        if default_action:
            table_entry.is_default_action = True

        if action_name:
            action = table_entry.action.action
            action.CopyFrom(self.build_action(action_name, action_params))

        if group_id:
            table_entry.action.action_profile_group_id = group_id

        return table_entry

    def build_meter_entry(self, meter_name, idx, cir=None, cburst=None, pir=None, pburst=None):
        meter_entry = p4runtime_pb2.MeterEntry()
        meter_entry.meter_id = self.get_meters_id(meter_name)

        meter_entry.index.index = idx
        # configure Meter Config only if we have all the required fields
        if cir is not None and cburst is not None and pir is not None and pburst is not None:
            meter_entry.config.cir = cir
            meter_entry.config.cburst = cburst
            meter_entry.config.pir = pir
            meter_entry.config.pburst = pburst
        return meter_entry

    def build_action(self, action_name, action_params=None):
        action = p4runtime_pb2.Action()
        action.action_id = self.get_actions_id(action_name)
        if action_params:
            action.params.extend([
                self.get_action_param_pb(action_name, field_name, value)
                for field_name, value in action_params.items()
            ])
        return action

    def build_act_prof_member(self, act_prof_name, action_name, action_params=None, member_id=None):
        member = p4runtime_pb2.ActionProfileMember()
        member.action_profile_id = self.get_action_profiles_id(act_prof_name)
        member.member_id = member_id if member_id else self.get_next_mbr_id()
        member.action.CopyFrom(self.build_action(action_name, action_params))
        return member

    def build_act_prof_group(self, act_prof_name, group_id, actions=()):
        messages = []
        group = p4runtime_pb2.ActionProfileGroup()
        group.action_profile_id = self.get_action_profiles_id(act_prof_name)
        group.group_id = group_id
        for action in actions:
            action_name = action[0]
            if len(action) > 1:
                action_params = action[1]
            else:
                action_params = None
            member = self.build_act_prof_member(act_prof_name, action_name, action_params)
            messages.extend([member])
            group_member = p4runtime_pb2.ActionProfileGroup.Member()
            group_member.member_id = member.member_id
            group_member.weight = 1
            group.members.extend([group_member])
        messages.append(group)
        return messages

    def build_packet_out(self, payload, metadata=None):
        packet_out = p4runtime_pb2.PacketOut()
        packet_out.payload = bytes(payload)
        if not metadata:
            return packet_out
        for name, value in metadata.items():
            p4info_meta = self.get_packet_metadata("packet_out", name)
            meta = packet_out.metadata.add()
            meta.metadata_id = p4info_meta.id
            meta.value = encode(value, p4info_meta.bitwidth)
        return packet_out

    def build_packet_in(self, payload, metadata=None):
        packet_in = p4runtime_pb2.PacketIn()
        packet_in.payload = bytes(payload)
        if not metadata:
            return packet_in
        for name, value in metadata.items():
            p4info_meta = self.get_packet_metadata("packet_in", name)
            meta = packet_in.metadata.add()
            meta.metadata_id = p4info_meta.id
            meta.value = encode(value, p4info_meta.bitwidth)
        return packet_in

    def build_digest_entry(self, digest_name, max_timeout_ns, max_list_size, ack_timeout_ns):
        digest_entry = p4runtime_pb2.DigestEntry()
        digest_entry.digest_id = self.get_digests_id(digest_name)
        config = digest_entry.config
        config.max_timeout_ns = max_timeout_ns
        config.max_list_size = max_list_size
        config.ack_timeout_ns = ack_timeout_ns
        return digest_entry

    def build_p4data_bitstring(self, value):
        data = p4data_pb2.P4Data()
        data.bitstring = value
        return data

    def build_p4data_struct(self, members):
        data = p4data_pb2.P4Data()
        struct = data.struct
        for m in members:
            x = struct.members.add()
            x.CopyFrom(m)
        return data
